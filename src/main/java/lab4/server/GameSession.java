package lab4.server;

import java.util.ArrayList;
import java.util.List;

import lab4.common.Board;
import lab4.common.JsonUtil;
import lab4.common.Move;

/**
 * Singleton: jedna sesja gry.
 *
 * Wzorce:
 * - Singleton: GameSession.getInstance()
 * - Observer (prymitywny): trzymamy listę ClientHandler i broadcastujemy
 * - DTO (data transfer object): Board i Move są przesyłane/serializowane przez JsonUtil
 */
public class GameSession
{
    // konstruktor tego jest prywatny, uzywajac getinstance wstawiamy instancje w pole static, a:
    /** Singleton instance */
    private static GameSession instance = null; //static nalezy do klasy, ale nie do instancji.

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
     * Returns true if the game is still running.
     *
     * @return true if game not over
     */
    public synchronized boolean isRunning(){ // do petli servermain, zeby wiedziec jak dlugo podtrzymywac
        return !gameOver;
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
        started = true;
        gameOver = false;
        currentPlayer = 1;
        consecutivePasses = 0;
        previousBoard = null;

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
        if (!gameOver)
        {
            gameOver = true;
            for (ClientHandler o : observers)
            {
                o.sendLine("ERROR Opponent disconnected. Game ended.");
                o.sendLine("GAME_OVER Opponent disconnected");
            }
        }
    }
}