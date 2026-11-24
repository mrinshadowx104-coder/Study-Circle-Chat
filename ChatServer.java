import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChatServer {
    private static final String HELP = "Commands: /name <newname>  /ask <question>  /answer <number>  "
            + "/poll <question>;<option1>;<option2>  /vote <number>  /help  /exit";
    private ArrayList<ClientHandler> clients = new ArrayList<ClientHandler>();
    private ArrayList<String> questions = new ArrayList<String>();
    private ArrayList<String> options = new ArrayList<String>();
    private ArrayList<Integer> votes = new ArrayList<Integer>();
    private ArrayList<ClientHandler> voters = new ArrayList<ClientHandler>();
    private String pollQuestion = "";
    private int nextId = 1;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int port = 0;
        while (port < 1024 || port > 65535) {
            System.out.print("Enter port (1024-65535): ");
            try { port = Integer.parseInt(scanner.nextLine().trim()); } catch (NumberFormatException e) { port = 0; }
        }
        new ChatServer().start(port);
    }

    public void start(int port) {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server running on port " + port);
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress a : Collections.list(n.getInetAddresses()))
                    if (a instanceof Inet4Address && !a.isLoopbackAddress())
                        System.out.println("Clients on the same network connect to: " + a.getHostAddress());
            while (true) new ClientHandler(serverSocket.accept(), this).start();
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }

    public synchronized String addClient(ClientHandler h, String nick) {
        String problem = checkNick(nick, h);
        if (problem != null) return problem;
        h.nick = nick;
        clients.add(h);
        h.send("OK|" + nick);
        sendAll("SYS|" + nick + " joined the circle.");
        sendAll(usersLine());
        h.send(join("QUEUE|", questions));
        h.send(pollLine());
        h.send("SYS|" + HELP);
        return null;
    }

    public synchronized void removeClient(ClientHandler h) {
        if (!clients.remove(h)) return;
        voters.remove(h);
        sendAll("SYS|" + h.nick + " left the circle.");
        sendAll(usersLine());
    }

    private String checkNick(String nick, ClientHandler me) {
        if (nick.equals("")) return "Nickname cannot be blank.";
        if (!nick.matches("[A-Za-z0-9_-]{1,15}")) return "Nickname must be 1-15 letters, digits, _ or -.";
        for (ClientHandler c : clients)
            if (c != me && c.nick.equalsIgnoreCase(nick)) return "That nickname is already in use.";
        return null;
    }

    public synchronized void handleMessage(ClientHandler h, String message) {
        String text = message.trim();
        if (text.equals("")) { h.send("ERR|Message cannot be blank."); return; }
        if (!text.startsWith("/")) {
            sendAll("CHAT|" + new SimpleDateFormat("HH:mm").format(new Date()) + "|" + h.nick + "|" + text);
            return;
        }
        String[] parts = text.split(" ", 2);
        String command = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim().replace("|", " ").replace("~", " ") : "";

        if (command.equals("/help")) {
            h.send("SYS|" + HELP);
        } else if (command.equals("/exit")) {
            h.send("BYE|");
            h.close();
        } else if (command.equals("/name")) {
            String problem = checkNick(arg, h);
            if (problem != null) { h.send("ERR|" + problem); return; }
            String old = h.nick;
            h.nick = arg;
            h.send("OK|" + arg);
            sendAll("SYS|" + old + " is now " + arg + ".");
            sendAll(usersLine());
        } else if (command.equals("/ask")) {
            if (arg.equals("")) { h.send("ERR|Usage: /ask <question>"); return; }
            questions.add(nextId + ". " + h.nick + ": " + arg);
            nextId++;
            sendAll("SYS|" + h.nick + " added a question.");
            sendAll(join("QUEUE|", questions));
        } else if (command.equals("/answer")) {
            int found = -1;
            for (int i = 0; i < questions.size(); i++)
                if (questions.get(i).startsWith(arg + ". ")) found = i;
            if (found == -1) { h.send("ERR|No question with that number."); return; }
            questions.remove(found);
            sendAll("SYS|" + h.nick + " answered question " + arg + ".");
            sendAll(join("QUEUE|", questions));
        } else if (command.equals("/poll")) {
            String[] p = arg.split(";", -1);
            ArrayList<String> opts = new ArrayList<String>();
            for (int i = 1; i < p.length; i++)
                if (!p[i].trim().equals("")) opts.add(p[i].trim());
            if (p[0].trim().equals("") || opts.size() < 2 || opts.size() > 6) {
                h.send("ERR|Usage: /poll <question>;<option1>;<option2> (2-6 options)");
                return;
            }
            pollQuestion = p[0].trim();
            options = opts;
            votes.clear();
            for (int i = 0; i < opts.size(); i++) votes.add(0);
            voters.clear();
            sendAll("SYS|" + h.nick + " started a poll: " + pollQuestion);
            sendAll(pollLine());
        } else if (command.equals("/vote")) {
            int n = 0;
            try { n = Integer.parseInt(arg); } catch (NumberFormatException e) { n = 0; }
            if (pollQuestion.equals("")) h.send("ERR|There is no active poll.");
            else if (n < 1 || n > options.size()) h.send("ERR|Choose a number from 1 to " + options.size() + ".");
            else if (voters.contains(h)) h.send("ERR|You already voted.");
            else {
                voters.add(h);
                votes.set(n - 1, votes.get(n - 1) + 1);
                sendAll(pollLine());
            }
        } else {
            h.send("ERR|Unknown command. Type /help.");
        }
    }

    private void sendAll(String line) {
        for (ClientHandler c : clients) c.send(line);
    }

    private String join(String head, ArrayList<String> items) {
        String line = head;
        for (int i = 0; i < items.size(); i++) line += (i > 0 ? "~" : "") + items.get(i);
        return line;
    }

    private String usersLine() {
        ArrayList<String> names = new ArrayList<String>();
        for (ClientHandler c : clients) names.add(c.nick);
        return join("USERS|", names);
    }

    private String pollLine() {
        if (pollQuestion.equals("")) return "POLL|";
        ArrayList<String> lines = new ArrayList<String>();
        for (int i = 0; i < options.size(); i++)
            lines.add((i + 1) + ". " + options.get(i) + "  [" + votes.get(i) + "]");
        return join("POLL|" + pollQuestion + "|", lines);
    }
}
