package lab4.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

import lab4.common.Board;
import lab4.common.JsonUtil;

/**
 * Simple line-based connection to server.
 */
public class ClientConnection {
    /** Socket used for communication with the server */
    private final Socket socket;
    /** Input stream used to receive data from the server */
    private final BufferedReader in;
    /** Output stream used to send data to the server */
    private final PrintWriter out;

    /**
     * Creates a new connection to the server with the given host and port.
     *
     * @param host server address
     * @param port server port number
     * @throws IOException if the connection cannot be established
     */
    public ClientConnection(String host, int port) throws IOException {
        socket = new Socket(host, port); // tworzymy nowy socket i do niego mamy in i out (z niego)
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    /**
     * Sends a single line of text to the server.
     *
     * @param line text line to be sent
     */
    public void sendLine(String line) {
        out.println(line); // wypisuje line do strumienia out; czyli wysyla tekst do serwera !!!!
    }

    /**
     * Sends a player's move to the server in JSON format,
     * prefixed with the MOVE command.
     *
     * @param json move description in JSON format
     */
    public void sendMoveJson(String json) {
        sendLine("MOVE " + json);
    }

    /**
     * Starts listening for messages from the server in a separate thread.
     * Received messages are interpreted and forwarded to appropriate
     * methods of the MessageHandler.
     *
     * @param handler object responsible for handling server messages
     */
    public void startListening(MessageHandler handler) {
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) { //odbierane linie (z serwera)
                    if (line.startsWith("START ")) {
                        handler.onStart(Integer.parseInt(line.substring(6).trim()));
                    } else if (line.startsWith("BOARD ")) {
                        try {
                            Board b = JsonUtil.jsonToBoard(line.substring(6).trim());
                            handler.onBoard(b);
                        } catch (Exception e) {
                            System.err.println("Failed parse BOARD JSON: " + e.getMessage());
                        }
                    } else if (line.equals("YOUR_TURN")) {
                        handler.onYourTurn();
                    } else if (line.equals("OPPONENT_TURN")) {
                        handler.onOpponentTurn();
                    } else if (line.equals("AGREEMENT_ON")) {
                        handler.onstoppedForAgreement();
                    } else if (line.equals("AGREEMENT_OFF")) {
                        handler.offstoppedForAgreement();
                    } else if (line.startsWith("WYNIKI1 ")) {
                        int value = Integer.parseInt(line.substring(8));
                        handler.wynikiPierwszego(value);
                    } else if (line.startsWith("WYNIKI2 ")) {
                        int value = Integer.parseInt(line.substring(8));
                        handler.wynikiDrugiego(value);
                    }
                    else if (line.startsWith("INFO ")) {
                        handler.onInfo(line.substring(5));
                    } else if (line.startsWith("ERROR ")) {
                        handler.onError(line.substring(6));
                    } else if (line.startsWith("GAME_OVER")) {
                        handler.onGameOver(line.substring(9).trim());
                    } else {
                        handler.onUnknown(line);
                    }
                }
            } catch (IOException e) {
                handler.onDisconnect();
            }
        }, "ServerListener").start();
    }

    /**
     * Closes the connection to the server.
     */
    public void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    /**
     * Interface defining methods for handling messages
     * received from the server.
     */
    public interface MessageHandler {
        /**
         * Called when the game start information is received.
         *
         * @param myId identifier of the current player
         */
        void onStart(int myId);
        /**
         * Called when the current board state is received.
         *
         * @param b object representing the board
         */
        void onBoard(Board b);
        /** Indicates that it is the current player's turn */
        void onYourTurn();
         /** Indicates that it is the opponent's turn */
        void onOpponentTurn();
        /**
         * Passes an informational message from the server.
         *
         * @param msg message content
         */
        void onInfo(String msg);
        /**
         * Passes an error message from the server.
         *
         * @param msg error message content
         */
        void onError(String msg);
        /**
         * Informs that the game has ended.
         *
         * @param msg additional information about the game result
         */
        void onGameOver(String msg);
        /** Called when the connection to the server is lost */
        void onDisconnect();
        /**
         * Handles an unknown or unrecognized message.
         *
         * @param line full content of the received line
         */
        void onUnknown(String line);
        /** Indicates that the game has been paused due to an agreement */
        void onstoppedForAgreement();
        /** Indicates that the game has been resumed after the agreement */
        void offstoppedForAgreement();
        /**
         * Passes the result of the first player.
         *
         * @param a result value
         */
        void wynikiPierwszego(int a);
        /**
         * Passes the result of the second player.
         *
         * @param a result value
         */
        void wynikiDrugiego(int a);
    }
}