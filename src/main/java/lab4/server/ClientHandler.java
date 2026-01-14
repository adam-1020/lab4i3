package lab4.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

import lab4.common.JsonUtil;
import lab4.common.Move;

/**
 * Handles a single client connection.
 * Accepts text commands (line-based). Commands are parsed case-insensitively.
 *
 * Allowed commands:
 *  - MOVE {json}
 *  - PASS
 *  - RESIGN
 *
 * Sends back lines like: (wysyla np. GameSession)
 *  - INFO ...
 *  - ERROR ...
 *  - BOARD ...
 */

public class ClientHandler implements Runnable {
    /** Socket for communication with this client */
    private final Socket socket;
    /** Reader for incoming client messages */
    private final BufferedReader in;
    /** Writer for outgoing messages to client */
    private final PrintWriter out;
    /** Player ID assigned to this client */
    private final int playerId;

    /**
     * Creates a ClientHandler for a connected socket.
     *
     * @param socket connected client socket
     * @param playerId assigned player ID (1 or 2)
     * @throws IOException if socket streams cannot be opened
     */
    public ClientHandler(Socket socket, int playerId) throws IOException {
        this.socket = socket; // tutaj bierzemy socket (utworzony w ClientConnection) pozyskany przez serverSocket.accept() w ServerMain
        this.playerId = playerId;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    /**
     * Returns the player ID for this client.
     *
     * @return player ID
     */
    public int getPlayerId() { return playerId; }

    /**
     * Sends a line of text to the client.
     *
     * @param line text to send
     */
    public void sendLine(String line) { // tutaj wysylamy linie do klienta !!!
        try { out.println(line); } catch (Exception e) { System.err.println("Send failed to p" + playerId + ": " + e.getMessage()); }
    }

     /**
     * Main loop for reading and handling client commands.
     * <p>
     * Runs in its own thread. Parses commands and forwards them
     * to the singleton GameSession instance for processing.
     */
    @Override
    public void run() {
        try {
            sendLine("INFO Connected as player " + playerId);
            String raw;
            while ((raw = in.readLine()) != null) {
                if (raw == null) break;
                String trimmed = raw.trim();
                if (trimmed.isEmpty()) continue;

                // split into command and optional argument (like MOVE json)
                String[] parts = trimmed.split("\\s+", 2); // Rozdziel tekst po dowolnej liczbie białych znaków; Podziel maksymalnie na 2 części
                String cmd = parts[0].toUpperCase(); // komenda
                String arg = parts.length > 1 ? parts[1].trim() : ""; // argumenty

                switch (cmd) {
                    case "MOVE":
                        if (arg.isEmpty()) {
                            sendLine("ERROR MOVE requires JSON argument");
                        } else {
                            try {
                                Move m = JsonUtil.jsonToMove(arg);
                                m.player = this.playerId; // enforce player id !
                                GameSession.getInstance().applyMove(m, this); //przez obecnego clientHandlera obslugujemy move (wywolujac applyMove w GameSession)
                            } catch (IllegalArgumentException ex) {
                                sendLine("ERROR Bad move JSON: " + ex.getMessage());
                            }
                        }
                        break;

                    case "PASS":
                        GameSession.getInstance().playerPassed(this);
                        break;

                    case "RESIGN":
                        GameSession.getInstance().playerResigned(this);
                        break;

                    case "RESUME":
                        GameSession.getInstance().playerResume(this);
                        break;

                    case "FINISH":
                        GameSession.getInstance().playerVotedFinish(this);
                        break;

                    default:
                        sendLine("ERROR Unknown command: [" + cmd + "]");
                }
            }
        } catch (IOException e) {
            System.err.println("Client " + playerId + " disconnected: " + e.getMessage());
        } finally { //jak klient się zamknie to jego handler po stronie serwera to przechwyci, jak klient uzyje quit/exit to wywola sie resign (bez info o bledzie), a jak zamknie okno po prostu to clientdisconnected
            try { socket.close(); } catch (IOException ignored) {}
            try { GameSession.getInstance().clientDisconnected(this); } catch (Exception ignored) {}
        }
    }
}