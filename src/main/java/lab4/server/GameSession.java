package lab4.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import lab4.common.Board;
import lab4.common.JsonUtil;
import lab4.common.Move;
import lab4.database.GameEntity;
import lab4.database.GameRepository;
import lab4.database.MoveEntity;
import lab4.database.MoveRepository;

/**
 * Singleton: jedna sesja gry.
 * Zarządza stanem gry, graczami i zapisem do bazy.
 */
public class GameSession
{
    // konstruktor tego jest prywatny, uzywajac getinstance wstawiamy instancje w pole static, a:
    /** Singleton instance */
    private static GameSession instance = null; //static nalezy do klasy, ale nie do instancji.

    // Repozytoria Springowe
    private GameRepository gameRepository;
    private MoveRepository moveRepository;

    // Obiekt gry w bazie danych
    private GameEntity currentGameEntity;
    private int moveCounter = 0;

    // utwórz lub pobierz instancję (wywołujemy z ServerMain przy starcie)
     /**
     * Returns the singleton instance, creating it if necessary with a board size.
     *
     * @param boardSize size of the board
     * @return singleton GameSession instance
     */
    public static synchronized GameSession getInstance(int boardSize)
    {
        if (instance == null) instance = new GameSession(boardSize);
        return instance;
    }

    public void setRepositories(GameRepository gr, MoveRepository mr) {
        this.gameRepository = gr;
        this.moveRepository = mr;
    }

    // pobierz istniejącą instancję (np. z handlerów)
    /**
     * Returns the existing singleton instance.
     *
     * @return GameSession instance
     * @throws IllegalStateException if not initialized yet
     */
    public static synchronized GameSession getInstance()
    {
        if (instance == null) throw new IllegalStateException("GameSession not initialized");
        return instance;
    }

    /** Game board */
    private final Board board;
    /** Registered clients (observers) */
    private final List<ClientHandler> observers = new ArrayList<>();
    /** ID of the player whose turn it is */
    private int currentPlayer = 1;
    /** Flags for game state */
    private boolean started = false;
    private boolean gameOver = false;
    private int consecutivePasses = 0; // bo po 2x PASS konczymy gre
    private boolean stoppedForAgreement = false; // nowe pole, true po PASS+PASS
    private boolean ONEvotedForFinish = false; // zlicza do dwoch, wtedy zatrzymuje gre
    private boolean TWOvotedForFinish = false;
    /** Captured stones for each player (index 0 = player 1, index 1 = player 2) */
    final int[] wyniki = {0,0}; // 0 indeks -> zbite 1 gracza; 1 indeks -> zbite 2 gracza (do uzycia pozniej w gui)

    // previousBoard used to detect Ko (position before last move)
    /** Previous board state used to detect Ko */
    private int[][] previousBoard = null;

    /**
     * Private constructor for singleton.
     *
     * @param boardSize size of the board
     */
    private GameSession(int boardSize)
    {
        this.board = new Board(boardSize);
    }

    /**
     * Metoda wywoływana przez ServerMain w pętli.
     * Próbuje dodać gracza do sesji. Jeśli jest miejsce -> tworzy handler i wątek.
     */
    public synchronized void tryAddPlayer(Socket socket) {
        // Jeśli jest już 2 graczy, odrzucamy połączenie
        if (observers.size() >= 2) {
            try {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println("ERROR Server full (game in progress). Try again later.");
                socket.close();
            } catch (IOException e) {
                System.err.println("Error rejecting client: " + e.getMessage());
            }
            return;
        }

        // Przydzielamy ID: 1 jeśli lista pusta, 2 jeśli jest już jeden
        int newId = observers.size() + 1;

        try {
            ClientHandler handler = new ClientHandler(socket, newId);
            register(handler);
            new Thread(handler).start();
            System.out.println("Player joined with ID: " + newId);

            // Jeśli po dodaniu mamy 2 graczy -> START GRY
            if (observers.size() == 2) {
                System.out.println("Two players present. Starting game...");
                startGame();
            }
        } catch (IOException e) {
            System.err.println("Failed to create ClientHandler: " + e.getMessage());
        }
    }

    /**
     * Registers a client to this game session.
     *
     * @param h client handler
     */
    public synchronized void register(ClientHandler h)
    {
        if (observers.size() >= 2)
        {
            h.sendLine("ERROR Server already has two players");
            return;
        }
        observers.add(h);
    }

    /**
     * Starts the game if two players are registered.
     */
    public synchronized void startGame()
    {
        if (started) return;
        if (observers.size() != 2)
        {
            System.out.println("Need exactly 2 players to start game");
            return;
        }

        // 1. Zapis nowej gry w bazie
        if (gameRepository != null) {
            currentGameEntity = new GameEntity();
            currentGameEntity.setResult("In Progress");
            currentGameEntity = gameRepository.save(currentGameEntity);
            moveCounter = 0;
            System.out.println("Game will be saved to DB with ID: " + currentGameEntity.getId());
        }

        // 2. Czyszczenie planszy (WAŻNE, bo po porzedniej grze cos moglo zostac)
        board.clear();

        // 3. Reset flags
        started = true;
        gameOver = false;
        currentPlayer = 1;
        consecutivePasses = 0;
        stoppedForAgreement = false;
        ONEvotedForFinish = false;
        TWOvotedForFinish = false;
        previousBoard = null;
        wyniki[0] = 0;
        wyniki[1] = 0;

        // 4. Powiadomienie graczy
        for (ClientHandler h : observers) h.sendLine("START " + h.getPlayerId());
        broadcastBoard();
        notifyTurn();
    }

    /** Notifies clients whose turn it is. */
    private synchronized void notifyTurn()
    {
        for (ClientHandler h : observers)
        {
            if (h.getPlayerId() == currentPlayer) h.sendLine("YOUR_TURN"); //wysylamy do klienta ze jego ruch
            else h.sendLine("OPPONENT_TURN"); //albo ze kolej przeciwnika
        }
    }

    /** Broadcasts the current board state to all clients. */
    public synchronized void broadcastBoard()
    {
        String json = JsonUtil.boardToJson(board);
        for (ClientHandler h : observers) h.sendLine("BOARD " + json); //wysylamy klientowi board w json
    }

    /** Broadcasts an informational message to all clients. */
    private synchronized void broadcastInfo(String msg)
    {
        for (ClientHandler h : observers) h.sendLine("INFO " + msg);
    }

    // APPLY MOVE
    /**
     * Applies a move made by a client.
     *
     * @param m move
     * @param ch client handler
     */
    public synchronized void applyMove(Move m, ClientHandler ch)
    {
        if (stoppedForAgreement) {ch.sendLine("ERROR Game stopped. Use RESUME to continue game or FINISH if you have agreed.");return;}
        if (gameOver) { ch.sendLine("ERROR Game already finished"); return; }
        if (m.player != ch.getPlayerId()) { ch.sendLine("ERROR Player id mismatch"); return; }
        if (m.player != currentPlayer) { ch.sendLine("ERROR Not your turn"); return; }

        // backup before move (for Ko detection and possible rollback)
        int[][] before = board.getGridCopy();

        int result = board.applyMoveAndCapture(m.row, m.col, m.player);

        if (result == -1) { ch.sendLine("ERROR Field occupied or out of bounds"); return; }
        if (result == -2) { ch.sendLine("ERROR Suicide move not allowed"); return; }

        // detect Ko: new board equal to previousBoard -> illegal
        if (previousBoard != null && Board.gridsEqual(board.getGridCopy(), previousBoard))
        {
            // rollback
            board.setGridFromCopy(before);
            ch.sendLine("ERROR Ko rule: immediate recapture not allowed");
            return;
        }

        // move accepted: set previousBoard = before (position before this move)
        previousBoard = before;

        // Zapis ruchu do bazy
        if (moveRepository != null && currentGameEntity != null) {
            MoveEntity me = new MoveEntity(++moveCounter, m.row, m.col, m.player, currentGameEntity);
            moveRepository.save(me);
        }
        // reset consecutive passes
        consecutivePasses = 0;

        broadcastBoard();
        if (result > 0) broadcastInfo("Player " + m.player + " captured " + result + " stone(s).");
        wyniki[m.player-1]+=result; // update wyników i rozesłanie ich
        for (ClientHandler h : observers)
        {
            h.sendLine("WYNIKI1 " + wyniki[0]);
            h.sendLine("WYNIKI2 " + wyniki[1]);
        }
        // change turn
        currentPlayer = (currentPlayer == 1 ? 2 : 1);
        notifyTurn();
    }

    // PASS
    /**
     * Handles a PASS command from a client.
     *
     * @param ch client handler
     */
    public synchronized void playerPassed(ClientHandler ch)
    {
        if (stoppedForAgreement) {ch.sendLine("ERROR Game stopped. Use RESUME to continue game or FINISH if you have agreed.");return;}
        if (gameOver) { ch.sendLine("ERROR Game already finished"); return; }
        if (ch.getPlayerId() != currentPlayer) { ch.sendLine("ERROR Not your turn"); return; }

        broadcastInfo("Player " + currentPlayer + " passed.");

        // For Ko: treat pass as a move that sets previousBoard to current position
        previousBoard = board.getGridCopy();
        consecutivePasses++;

        if (consecutivePasses >= 2) {
            stoppedForAgreement = true;
            //powiadamiamy klientow
            for (ClientHandler h : observers)
            {
                h.sendLine("AGREEMENT_ON");
            }
            broadcastInfo("Both players passed. Game stopped for agreement.");
            broadcastInfo("Players may now agree on dead stones and type FINISH or request RESUME.");
            return; //nie musimy sie przejmowac ustawieniem currentPlayer
        }

        currentPlayer = (currentPlayer == 1 ? 2 : 1);
        notifyTurn();
    }

    // RESUME
    /**
     * Handles a RESUME command from a client.
     *
     * @param ch client handler
     */
    public synchronized void playerResume(ClientHandler ch)
    {
        if (!stoppedForAgreement) {
            ch.sendLine("ERROR Game is not stopped");
            return;
        }
        //powiadamiamy klientow
        stoppedForAgreement = false;
        for (ClientHandler h : observers)
        {
            h.sendLine("AGREEMENT_OFF");
        }
        consecutivePasses = 0;
        ONEvotedForFinish = false;
        TWOvotedForFinish = false;
        // przeciwnik gracza żądającego wznowienia zaczyna
        currentPlayer = (ch.getPlayerId() == 1 ? 2 : 1);
        broadcastBoard();
        broadcastInfo("Game resumed. Player " + currentPlayer + " to move.");
        notifyTurn();
    }

    // FINISH
    /**
     * Handles a FINISH vote from a client.
     *
     * @param ch client handler
     */
    public synchronized void playerVotedFinish(ClientHandler ch)
    {
    if (ch.getPlayerId() == 1){ONEvotedForFinish = true; broadcastInfo("Player 1 voted FINISH");}
    else if (ch.getPlayerId() == 2){TWOvotedForFinish = true; broadcastInfo("Player 2 voted FINISH");}
    if(ONEvotedForFinish && TWOvotedForFinish) {
        gameOver = true;
            if (currentGameEntity != null && gameRepository != null) {
                currentGameEntity.setResult("Finished by agreement");
                gameRepository.save(currentGameEntity);
            }
        for (ClientHandler h : observers) h.sendLine("GAME_OVER You both agreed. Thanks for game:)"); //konczy gre
    }
    }

    // RESIGN
    /**
     * Handles a RESIGN command from a client.
     *
     * @param ch client handler
     */
    public synchronized void playerResigned(ClientHandler ch)
    {
        if (gameOver) { ch.sendLine("ERROR Game already finished"); return; }
        int winner = (ch.getPlayerId() == 1 ? 2 : 1);
        gameOver = true;

        if (currentGameEntity != null && gameRepository != null) {
            currentGameEntity.setResult("Player " + ch.getPlayerId() + " resigned");
            gameRepository.save(currentGameEntity);
        }

        broadcastInfo("Player " + ch.getPlayerId() + " resigned. Player " + winner + " wins.");
        for (ClientHandler h : observers) h.sendLine("GAME_OVER Player " + winner + " wins (resign)");
    }

    // client disconnected
    /**
     * Handles client disconnection.
     *
     * @param ch client handler
     */
    public synchronized void clientDisconnected(ClientHandler ch)
    {
        observers.remove(ch);
        if (started && !gameOver)
        {
            gameOver = true;
            for (ClientHandler o : observers)
            {
                o.sendLine("ERROR Opponent disconnected. Game ended.");
                o.sendLine("GAME_OVER Opponent disconnected");
            }
            // Zapisz w bazie, ze przerwano
            if (currentGameEntity != null && gameRepository != null) {
                currentGameEntity.setResult("Aborted (Disconnect)");
                gameRepository.save(currentGameEntity);
            }
        }

        // Jeśli nikt nie został, resetujemy sesję dla nowych graczy
        if (observers.isEmpty()) {
            System.out.println("All players disconnected. Resetting session automatically.");
            reset();
        }
    }

    /**
     * Resets the session state so a new game can be played without restarting server.
     */
    public synchronized void reset() {
        // Powiadamiamy i usuwamy resztki graczy (jeśli reset wywołany ręcznie)
        for (ClientHandler h : observers) {
            try {h.sendLine("GAME_OVER Session reset by admin. Disconnecting.");} catch (Exception ignored) {}
        }
        observers.clear();

        // Czyszczenie planszy
        board.clear();

        this.previousBoard = null;
        this.started = false;
        this.gameOver = false;
        this.currentPlayer = 1;
        this.consecutivePasses = 0;
        this.stoppedForAgreement = false;
        this.ONEvotedForFinish = false;
        this.TWOvotedForFinish = false;
        this.wyniki[0] = 0;
        this.wyniki[1] = 0;

        this.currentGameEntity = null;
        this.moveCounter = 0;

        System.out.println("Game Session has been reset. Ready for new players.");
    }

    /**
     * Odtwarza gre z bazy danych.
     */
    public synchronized void replayGameFromDb(Long gameId) {
        if(started){
            System.out.println("Game in progress! Use 'reset' first.");
            return;
        }
        if(observers.isEmpty()){
            System.out.println("No players connected, cant show replay");
            return;
        }
        if (gameRepository == null) {
            System.out.println("DB not initialized");
            return;
        }
        GameEntity game = gameRepository.findById(gameId).orElse(null);
        if (game == null) {
            broadcastInfo("Game with ID " + gameId + " not found.");
            return;
        }

        broadcastInfo("REPLAYING GAME ID: " + gameId);

        // Czyścimy planszę przed odtwarzaniem
        board.clear();
        broadcastBoard();

        List<MoveEntity> moves = game.getMoves();
        for (MoveEntity me : moves) {
            try {
                // Opoznienie dla efektu wizualnego
                Thread.sleep(800);
            } catch (InterruptedException ignored) {}

            board.applyMoveAndCapture(me.getRowInd(), me.getColInd(), me.getPlayerId());
            broadcastBoard();
            broadcastInfo("Replay move: Player " + me.getPlayerId() + " at " + me.getRowInd() + "," + me.getColInd());
        }
        broadcastInfo("REPLAY FINISHED. Result: " + game.getResult());

        //zeby teraz zagrac wystarczy ze dolaczy drugi gracz
    }
}