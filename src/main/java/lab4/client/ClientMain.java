package lab4.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import lab4.common.Board;
import lab4.common.JsonUtil;
import lab4.common.Move;

/**
 * Console client:
 * - commands: row col  (MOVE), PASS, RESIGN, quit/exit
 * - trims and uppercases commands, so PASS/Resign/move are robust against whitespace/case
 */
public class ClientMain
{
    /**
     * Entry point of the console client application.
     * Establishes a connection to the server, sets up message handling,
     * and processes user input from the console.
     *
     * @param args command-line arguments (not used)
     * @throws IOException if an I/O error occurs while reading input
     */
     public static void main(String[] args) throws IOException
     {
        String host = "localhost";
        int port = 55555;

        final ClientConnection conn;
        try
        {
            conn = new ClientConnection(host, port);
        } catch (IOException e) {
            System.err.println("Failed to connect: " + e.getMessage());
            return;
        }

        /** Indicates whether it is currently this player's turn */
        final boolean[] myTurn = {false};
        /** Stores the identifier of this player */
        final int[] myId = {-1}; //tablica jednoelementowa; finalna; ale jej elementy mozna zmieniac
        /** Indicates whether the game is paused due to an agreement */
        final boolean[] stoppedForAgreement = {false};  // nowo dodane (jak true to czekamy na finish/resume)
        /**
         * Stores captured stones counts:
         * index 0 – first player,
         * index 1 – second player
         */
        final int[] wyniki = {0,0}; // 0 indeks -> zbite 1 gracza; 1 indeks -> zbite 2 gracza (do uzycia pozniej w gui)

        conn.startListening(new ClientConnection.MessageHandler() {
            @Override
            public void onStart(int myId0)
            {
                myId[0] = myId0;
                System.out.println("Game started. You are player " + myId0 + " (X=1, O=2)");
            }

            @Override
            public void onBoard(Board b)
            {
                System.out.println("--- BOARD ---");
                System.out.println(b.toString());
            }

            @Override
            public void onYourTurn()
            {
                myTurn[0] = true;
                System.out.println("Your turn. Enter: row col   (or type PASS or RESIGN)");
            }

            @Override
            public void onOpponentTurn()
            {
                myTurn[0] = false;
                System.out.println("Waiting for opponent...");
            }

            @Override
            public void onInfo(String msg)
            {
                System.out.println("[INFO] " + msg);
            }

            @Override
            public void onError(String msg)
            {
                System.err.println("[ERROR] " + msg);
            }

            @Override
            public void onGameOver(String msg)
            {
                System.out.println("[GAME OVER] " + msg);
                System.exit(0);
            }

            @Override
            public void onDisconnect()
            {
                System.err.println("Disconnected from server.");
                System.exit(0);
            }

            @Override
            public void onUnknown(String line)
            {
                System.out.println("[SERVER] " + line);
            }

            @Override
            public void onstoppedForAgreement() {
                stoppedForAgreement[0] = true;
            }

            @Override
            public void offstoppedForAgreement() {
                stoppedForAgreement[0] = false;
            }

            @Override
            public void wynikiPierwszego(int a) {
                wyniki[0]=a;
            }

            @Override
            public void wynikiDrugiego(int a) {
                wyniki[1]=a;
            }
        });

        System.out.println("Connected. Wait until game starts...");
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        while (true) 
        {
            String raw = console.readLine();
            if (raw == null) break;
            String line = raw.trim();
            if (line.isEmpty()) continue;

            String up = line.toUpperCase();

            // immediate quit/exit/resign
            if (up.equals("QUIT") || up.equals("EXIT"))
            {
                conn.sendLine("RESIGN");
                conn.close();
                break;
            }

            if (up.equals("RESIGN"))
            {
                conn.sendLine("RESIGN");
                continue;
            }

            if (up.equals("PASS"))
            {
                conn.sendLine("PASS");
                continue;
            }

            if (up.equals("RESUME"))
            {
                conn.sendLine("RESUME");
                continue;
            }

            if (up.equals("SCORE"))
            {
                System.out.println("Player1: " + wyniki[0] + ", Plater2: " + wyniki[1]);
                continue;
            }

            if (stoppedForAgreement[0]){ //nie jest w 100% konieczne, bo i tak serwer by zwrocil błąd na requesty w takiej sytuacji, ale ten bool sie przyda pozniej do gui
                if (up.equals("FINISH")) {
                    conn.sendLine("FINISH");
                    continue;
                }
                System.out.println("Game stopped. Use FINISH or RESUME to continue!");
                continue;
            }

            // otherwise try to parse move row col
            if (!myTurn[0])
            {
                System.out.println("Not your turn yet.");
                continue;
            }

            String[] parts = line.split("\\s+"); // \s -> dowolny znak biały; jeden lub wiecej takich znakow
            if (parts.length < 2)
            {
                System.out.println("Bad input. Use: row col   or PASS   or RESIGN");
                continue;
            }

            try {
                int r = Integer.parseInt(parts[0]);
                int c = Integer.parseInt(parts[1]);
                Move m = new Move(r, c, myId[0]);
                String json = JsonUtil.moveToJson(m);
                conn.sendMoveJson(json);
            } catch (NumberFormatException e) {
                System.out.println("Bad numbers: " + e.getMessage());
            }
        }
    }
}