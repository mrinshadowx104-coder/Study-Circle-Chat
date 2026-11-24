import java.io.*;
import java.net.*;

public class ClientHandler extends Thread {

    public String nick = "";
    private Socket socket;
    private ChatServer server;
    private PrintWriter out;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    public synchronized void send(String line) {
        if (out != null) out.println(line);
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
        }
    }

    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            String first = in.readLine();
            if (first == null || !first.startsWith("NICK|")) return;
            String problem = server.addClient(this, first.substring(5).trim());
            if (problem != null) {
                send("ERR|" + problem);
                return;
            }
            String line;
            while ((line = in.readLine()) != null)
                if (line.startsWith("MSG|")) server.handleMessage(this, line.substring(4));
        } catch (IOException e) {
        } finally {
            server.removeClient(this);
            close();
        }
    }
}
