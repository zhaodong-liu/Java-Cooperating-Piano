import java.io.*;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;


public class PianoServer {
    private static final int PORT = 5190;
    private static final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Piano Server running on port " + PORT + "...");

        while (true) {
            Socket socket = serverSocket.accept();
            ClientHandler handler = new ClientHandler(socket);
            clients.add(handler);
            new Thread(handler).start();
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private final BufferedReader in;
        private final PrintWriter out;
        private String username = "Anonymous";

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
        }

        public synchronized void sendMessage(String msg) {
            out.println(msg);
        }

        @Override
        public void run() {
            try {
                // first message received is the username
                username = in.readLine();
                if (username == null) {
                    closeConnection();
                    return;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                broadcastSystemMessage(username + " has entered the room.");

                String msg;
                while ((msg = in.readLine()) != null) {
                    int firstComma = msg.indexOf(',');
                    // System.out.println("[SERVER RECEIVE] Got: " + msg);
                    if (firstComma == -1) {
                        continue;
                    }

                    String category = msg.substring(0, firstComma);
                    String content = msg.substring(firstComma + 1);

                    if ("MUSIC".equals(category)) {
                        broadcastMusicMessage(content);
                    } else if ("CHAT".equals(category)) {
                        broadcastChatMessage(username + ": " + content);
                    }
                }
            } catch (IOException e) {
                System.out.println("Client disconnected: " + username);
            } finally {
                clients.remove(this);
                broadcastSystemMessage(username + " has left the room.");
                closeConnection();
            }
        }

        private void broadcastMusicMessage(String musicContent) {
            for (ClientHandler client : clients) {
                if (client != this) {
                    client.sendMessage("MUSIC," + musicContent);
                    // System.out.println("[SERVER BROADCAST] Broadcasting to all: MUSIC," + musicContent);
                }
            }
        }

        private void broadcastChatMessage(String chatContent) {
            for (ClientHandler client : clients) {
                client.sendMessage("CHAT," + chatContent);
            }
        }

        private void broadcastSystemMessage(String systemMessage) {
            String fullMessage = systemMessage + " (Current users: " + clients.size() + ")";
            for (ClientHandler client : clients) {
                client.sendMessage("CHAT,[System]: " + fullMessage);
            }
        }

        private void closeConnection() {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}