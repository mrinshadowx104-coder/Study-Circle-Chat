import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.swing.*;

public class ChatClient extends JFrame {
    private JTextField txtHost = new JTextField(10);
    private JTextField txtPort = new JTextField(5);
    private JTextField txtNick = new JTextField(8);
    private JTextField txtMessage = new JTextField();
    private JButton btnConnect = new JButton("Connect");
    private JButton btnDisconnect = new JButton("Disconnect");
    private JButton btnSend = new JButton("Send");
    private JButton btnAsk = new JButton("Ask question");
    private JButton btnAnswer = new JButton("Mark answered");
    private JButton btnNewPoll = new JButton("New poll");
    private JButton btnVote = new JButton("Vote");
    private JTextArea txtChat = new JTextArea();
    private JTextArea txtPoll = new JTextArea(6, 18);
    private JList<String> lstUsers = new JList<String>();
    private JList<String> lstQueue = new JList<String>();

    public ChatClient() {
        super("Study Circle");
        buildWindow();
        initLogic();
    }

    private void buildWindow() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Server IP:"));
        top.add(txtHost);
        top.add(new JLabel("Port:"));
        top.add(txtPort);
        top.add(new JLabel("Nickname:"));
        top.add(txtNick);
        top.add(btnConnect);
        top.add(btnDisconnect);

        JPanel queueButtons = new JPanel();
        queueButtons.add(btnAsk);
        queueButtons.add(btnAnswer);
        JPanel pollButtons = new JPanel();
        pollButtons.add(btnNewPoll);
        pollButtons.add(btnVote);
        JPanel side = new JPanel(new GridLayout(3, 1));
        side.setPreferredSize(new Dimension(280, 0));
        side.add(box("Online", lstUsers, null));
        side.add(box("Question queue", lstQueue, queueButtons));
        side.add(box("Live poll", txtPoll, pollButtons));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(txtMessage, BorderLayout.CENTER);
        bottom.add(btnSend, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(txtChat), BorderLayout.CENTER);
        add(side, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        ActionListener listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Object s = e.getSource();
                if (s == btnConnect) connectToServer();
                else if (s == btnDisconnect) disconnectFromServer();
                else if (s == btnSend || s == txtMessage) sendMessage();
                else if (s == btnAsk) askQuestion();
                else if (s == btnAnswer) answerSelectedQuestion();
                else if (s == btnNewPoll) startPoll();
                else if (s == btnVote) castVote();
            }
        };
        for (JButton b : new JButton[]{btnConnect, btnDisconnect, btnSend, btnAsk, btnAnswer, btnNewPoll, btnVote})
            b.addActionListener(listener);
        txtMessage.addActionListener(listener);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { disconnectFromServer(); }
        });
        setSize(900, 560);
        setLocationRelativeTo(null);
    }

    private JPanel box(String title, JComponent content, JPanel buttons) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(new JScrollPane(content), BorderLayout.CENTER);
        if (buttons != null) panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private Socket socket;
    private volatile PrintWriter out;
    private BufferedReader in;
    private DefaultListModel<String> usersModel = new DefaultListModel<String>();
    private DefaultListModel<String> queueModel = new DefaultListModel<String>();
    private boolean accepted = false;
    private boolean leaving = false;

    private void initLogic() {
        lstUsers.setModel(usersModel);
        lstQueue.setModel(queueModel);
        txtChat.setEditable(false);
        txtChat.setLineWrap(true);
        txtPoll.setEditable(false);
        txtPoll.setText("No active poll.");
        setConnected(false);
    }

    private void setConnected(boolean c) {
        txtHost.setEnabled(!c); txtPort.setEnabled(!c); txtNick.setEnabled(!c); btnConnect.setEnabled(!c);
        btnDisconnect.setEnabled(c); txtMessage.setEnabled(c); btnSend.setEnabled(c);
        btnAsk.setEnabled(c); btnAnswer.setEnabled(c); btnNewPoll.setEnabled(c); btnVote.setEnabled(c);
    }

    private void warn(String message) {
        JOptionPane.showMessageDialog(this, message, "Study Circle", JOptionPane.WARNING_MESSAGE);
    }

    private void chat(String line) {
        txtChat.append(line + "\n");
        txtChat.setCaretPosition(txtChat.getDocument().getLength());
    }

    private void fill(DefaultListModel<String> model, String text) {
        model.clear();
        for (String item : text.split("~"))
            if (!item.equals("")) model.addElement(item);
    }

    private void connectToServer() {
        final String host = txtHost.getText().trim();
        final String nick = txtNick.getText().trim();
        final int port;
        try { port = Integer.parseInt(txtPort.getText().trim()); }
        catch (NumberFormatException e) { warn("The port must be a number."); return; }
        if (host.equals("") || nick.equals("") || port < 1 || port > 65535) {
            warn("Enter a server IP, a port from 1 to 65535, and a nickname.");
            return;
        }
        btnConnect.setEnabled(false);
        accepted = false;
        leaving = false;
        chat("*** Connecting to " + host + ":" + port + " ...");
        new Thread() {
            public void run() {
                try {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(host, port), 5000);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                } catch (IOException e) {
                    toGui("FAIL|Could not connect. Check the IP, the port, and that the server is running.");
                    return;
                }
                out.println("NICK|" + nick);
                try {
                    String line;
                    while ((line = in.readLine()) != null) toGui(line);
                } catch (IOException e) { }
                toGui("END|");
            }
        }.start();
    }

    private void toGui(final String line) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { handleServerLine(line); }
        });
    }

    private void closeConnection() {
        try { if (socket != null) socket.close(); } catch (IOException e) { }
        socket = null;
        out = null;
    }

    private void handleServerLine(String line) {
        String[] p = line.split("\\|", 4);
        if (p[0].equals("OK")) {
            accepted = true;
            txtNick.setText(p[1]);
            setTitle("Study Circle - " + p[1]);
            setConnected(true);
        } else if (p[0].equals("CHAT") && p.length == 4) {
            chat("[" + p[1] + "] " + p[2] + ": " + p[3]);
        } else if (p[0].equals("SYS")) {
            chat("*** " + line.substring(4));
        } else if (p[0].equals("ERR")) {
            chat("[!] " + line.substring(4));
            if (!accepted) warn(line.substring(4));
        } else if (p[0].equals("USERS")) {
            fill(usersModel, line.substring(6));
        } else if (p[0].equals("QUEUE")) {
            fill(queueModel, line.substring(6));
        } else if (p[0].equals("POLL")) {
            txtPoll.setText(p.length < 3 ? "No active poll." : p[1] + "\n\n" + p[2].replace("~", "\n"));
        } else if (p[0].equals("FAIL")) {
            chat("*** " + line.substring(5));
            warn(line.substring(5));
            closeConnection();
            setConnected(false);
        } else if (p[0].equals("BYE") || p[0].equals("END")) {
            boolean wasConnected = accepted;
            accepted = false;
            closeConnection();
            setConnected(false);
            usersModel.clear();
            queueModel.clear();
            txtPoll.setText("No active poll.");
            setTitle("Study Circle");
            if (wasConnected && leaving) chat("*** You left the circle.");
            else if (wasConnected) { chat("*** Connection to the server was lost."); warn("Connection to the server was lost."); }
        }
    }

    private void sendRaw(String text) {
        if (out != null) out.println("MSG|" + text);
    }

    private void sendMessage() {
        String text = txtMessage.getText().trim();
        if (text.equals("")) { chat("[!] Message cannot be blank."); return; }
        sendRaw(text);
        txtMessage.setText("");
    }

    private void disconnectFromServer() {
        leaving = true;
        sendRaw("/exit");
    }

    private void askQuestion() {
        String q = JOptionPane.showInputDialog(this, "Your question for the group:");
        if (q != null && !q.trim().equals("")) sendRaw("/ask " + q.trim());
    }

    private void answerSelectedQuestion() {
        String selected = lstQueue.getSelectedValue();
        if (selected == null) { warn("Click a question in the queue first."); return; }
        sendRaw("/answer " + selected.substring(0, selected.indexOf('.')));
    }

    private void startPoll() {
        String q = JOptionPane.showInputDialog(this, "Poll question:");
        if (q == null) return;
        String o = JOptionPane.showInputDialog(this, "Options separated by ; (2 to 6):");
        if (o != null) sendRaw("/poll " + q.trim() + ";" + o.trim());
    }

    private void castVote() {
        String n = JOptionPane.showInputDialog(this, "Option number to vote for:");
        if (n != null) sendRaw("/vote " + n.trim());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { new ChatClient().setVisible(true); }
        });
    }
}
