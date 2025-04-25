import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class PianoServer {
    private static final int PORT = 5190;
    private static final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("🎼 Piano Server running on port " + PORT + "...");

        while (true) {
            Socket socket = serverSocket.accept();
            ClientHandler handler = new ClientHandler(socket);
            clients.add(handler);
            new Thread(handler).start();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username = "Anonymous";

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        }

        public void sendMessage(String msg) {
            out.println(msg);
        }

        @Override
        public void run() {
            try {
                // First message received should be the username
                username = in.readLine();
                broadcast("[System]: " + username + " has entered the room.");

                String msg;
                while ((msg = in.readLine()) != null) {
                    String[] parts = msg.split(",", 2);
                    if (parts.length >= 2) {
                        String category = parts[0];
                        String content = parts[1];

                        if (category.equals("MUSIC")) {
                            // MUSIC: send to all except the sender
                            for (ClientHandler client : clients) {
                                if (client != this) {
                                    client.sendMessage("MUSIC," + content);
                                }
                            }
                        } else if (category.equals("CHAT")) {
                            // CHAT: send to everyone, username prepended
                            broadcast(username + ": " + content);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Client disconnected.");
            } finally {
                clients.remove(this);
                broadcast("[System]: " + username + " has left the room.");
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }

        private void broadcast(String message) {
            for (ClientHandler client : clients) {
                client.sendMessage("CHAT," + message);
            }
        }
    }
}