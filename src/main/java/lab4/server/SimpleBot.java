package lab4.server;

import lab4.common.Board;
import lab4.common.JsonUtil;
import lab4.common.Move;

import java.io.*;
import java.net.Socket;
import java.util.Random;

/**
 * Prosty bot działający jako klient na serwerze.
 * Łączy się do localhost i wykonuje poprawne ruchy (z uwzględnieniem KO i prostej taktyki).
 */
public class SimpleBot implements Runnable {

    private final String host = "localhost";
    private final int port = 55555;
    private final Random random = new Random();

    private Board currentBoard;
    // Zapamiętujemy planszę sprzed ruchu przeciwnika, aby wykryć KO
    private int[][] gridBeforeOpponentMove = null;

    private int myId;

    @Override
    public void run() {
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {

            String line;
            boolean connectionConfirmed = false;

            while ((line = in.readLine()) != null) {

                // --- Logika połączenia ---
                if (!connectionConfirmed) {
                    if (line.startsWith("ERROR")) {
                        System.err.println("[BOT] Connection rejected by server: " + line);
                        return;
                    } else {
                        System.out.println("[BOT] Connected to server.");
                        connectionConfirmed = true;
                    }
                }

                // --- Odbieranie komunikatów ---
                if (line.startsWith("START ")) {
                    myId = Integer.parseInt(line.substring(6).trim());
                    System.out.println("[BOT] I am player " + myId);
                }
                else if (line.startsWith("BOARD ")) {
                    try {
                        // Zanim zaktualizujemy currentBoard o ruch przeciwnika,
                        // zapisujemy jej obecny stan jako "historię" dla reguły KO.
                        if (currentBoard != null) {
                            gridBeforeOpponentMove = currentBoard.getGridCopy();
                        }

                        currentBoard = JsonUtil.jsonToBoard(line.substring(6).trim());
                    } catch (Exception e) {
                        System.err.println("[BOT] Parse error: " + e.getMessage());
                    }
                }
                else if (line.equals("YOUR_TURN")) {
                    makeMove(out);
                }
                else if (line.equals("AGREEMENT_ON")) {
                    // Jak spasowalismy 2 razy, to też kończymy
                    System.out.println("[BOT] Opponent passed. I vote FINISH.");
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    out.println("FINISH");
                }
                else if (line.startsWith("GAME_OVER")) {
                    System.out.println("[BOT] Game over. Exiting.");
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("[BOT] Connection error: " + e.getMessage());
        }
    }

    private void makeMove(PrintWriter out) {
        // Symulacja myślenia
        try { Thread.sleep(700); } catch (InterruptedException ignored) {}

        if (currentBoard == null) return;

        int size = currentBoard.size;
        int bestR = -1;
        int bestC = -1;
        int maxScore = -10000;

        // Robimy kopię planszy, żeby móc na niej "brudzić" testując ruchy
        int[][] originalGrid = currentBoard.getGridCopy();

        // Przeglądamy każde pole planszy
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                // Interesują nas tylko puste pola
                if (currentBoard.grid[r][c] != 0) continue;

                // --- SYMULACJA RUCHU ---
                currentBoard.setGridFromCopy(originalGrid); // Reset przed testem

                // Wykonujemy próbny ruch na obiekcie Board
                int captureResult = currentBoard.applyMoveAndCapture(r, c, myId);

                // 1. Sprawdzenie legalności podstawowej (zajęte/poza planszą/samobójstwo)
                if (captureResult < 0) continue;

                // 2. SPRAWDZENIE KO
                // Jeśli wynikowa plansza jest identyczna jak ta sprzed ruchu przeciwnika -> KO -> Zabronione
                if (gridBeforeOpponentMove != null && Board.gridsEqual(currentBoard.grid, gridBeforeOpponentMove)) {
                    // To jest sytuacja KO
                    continue;
                }

                // --- OCENA RUCHU ---

                // A. Jeśli ruch coś zbija -> PRIORYTET ABSOLUTNY
                if (captureResult > 0) {
                    // Przywracamy planszę do stanu faktycznego przed wysłaniem ruchu
                    currentBoard.setGridFromCopy(originalGrid);
                    sendMove(out, r, c);
                    return; // Kończymy szukanie, gramy od razu
                }

                // B. Ocena pozycyjna (gdy nie ma bicia)
                int score = random.nextInt(10); // Losowy czynnik

                // Analiza sąsiadów
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                boolean hasNeighbor = false;

                for (int[] d : dirs) {
                    int nr = r + d[0];
                    int nc = c + d[1];
                    if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                        int neighbor = originalGrid[nr][nc];
                        if (neighbor != 0) {
                            hasNeighbor = true;
                            if (neighbor == myId) {
                                score += 2; // Łączenie grup
                            } else {
                                score += 8; // Kontakt z wrogiem (agresja/blok)
                            }
                        }
                    }
                }

                // Kara za granie w "kosmosie" (z dala od innych kamieni)
                if (!hasNeighbor) score -= 5;

                // Kara za krawędzie (chyba że jest tam walka)
                if (isEdge(r, size) || isEdge(c, size)) {
                    score -= 15;
                }

                // Aktualizacja najlepszego ruchu
                if (score > maxScore) {
                    maxScore = score;
                    bestR = r;
                    bestC = c;
                }
            }
        }

        // Przywracamy planszę po wszystkich symulacjach
        currentBoard.setGridFromCopy(originalGrid);

        // Wykonujemy najlepszy ruch
        if (bestR != -1) {
            sendMove(out, bestR, bestC);
        } else {
            // Brak legalnych/sensownych ruchów -> PASS
            out.println("PASS");
        }
    }

    private boolean isEdge(int coord, int size) {
        return coord == 0 || coord == size - 1;
    }

    private void sendMove(PrintWriter out, int r, int c) {
        Move m = new Move(r, c, myId);
        String json = JsonUtil.moveToJson(m);
        out.println("MOVE " + json);
        System.out.println("[BOT"+myId+"] Sent move: " + r + ", " + c);
    }
}