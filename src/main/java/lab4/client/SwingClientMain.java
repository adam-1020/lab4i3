package lab4.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.time.LocalTime;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import lab4.common.Board;
import lab4.common.JsonUtil;
import lab4.common.Move;

/**
 * Swing GUI client (działa jak ClientMain konsolowy, można ich używać zamiennie)
 * - boczna konsola (stylowana, auto-scroll)
 * - wyraźny panel statusu (czyja tura / agreement)
 * - estetyczny panel punktów obu graczy (przydatne przy ustalaniu punktów, żeby wiedzieć kto ile zbił)
 * - kamienie na przecieciach linii
 * - zgodne z logiką serwera
 */
public class SwingClientMain {
    // networking / state

    /** Connection to the game server */
    private ClientConnection conn;
    /** Identifier of this player */
    private int myId = -1;
    /** Indicates whether it is currently this player's turn */
    private boolean myTurn = false;
    /** Indicates whether the game is stopped due to an agreement */
    private boolean stoppedForAgreement = false;
    /** Stores captured stones count for both players */
    private final int[] wyniki = {0, 0};
    /** Current board state */
    private Board board;

    // Swing
    /** Main application window */
    private JFrame frame;
    /** Custom panel used to render the board */
    private BoardPanel boardPanel; // nasza klasa

    /** Displays turn information */
    private JLabel turnLabel;
    /** Displays agreement status */
    private JLabel agreementLabel;
    /** Displays score information */
    private JLabel scoreLabel;
    /** Displays player identification */
    private JLabel playerLabel;

    /** Button for passing a turn */
    private JButton passBtn;
    /** Button for resigning from the game */
    private JButton resignBtn;
    /** Button for resuming the game after agreement */
    private JButton resumeBtn;
    /** Button for finishing the agreement phase */
    private JButton finishBtn;

    /** Text console for logging messages */
    private JTextPane console;
    /** Styled document backing the console */
    private StyledDocument consoleDoc;

    /**
     * Application entry point.
     * Ensures that the GUI is created on the Swing event dispatch thread.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SwingClientMain().start()); // kod wewnątrz niej (stworzenie lambdą SwingClientMain oraz wywołanie start) zostanie wykonany na głównym wątku aplikacji Swing
    }

    /**
     * Initializes the client connection and builds the GUI.
     */
    private void start() {
        try {
            conn = new ClientConnection("localhost", 55555);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Connection failed: " + e.getMessage());
            return;
        }
        // jak połączenie się powiedzie
        buildGui();
        registerHandlers();
    }

    // GUI
    /**
     * Builds and initializes the main GUI layout.
     */
    private void buildGui() {
        frame = new JFrame("SwingClient"); // nazwa okna aplikacji
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout()); // N E W S / Center

        boardPanel = new BoardPanel(); // tworzymy instancja naszej klasy
        frame.add(boardPanel, BorderLayout.CENTER); // boardPanel na środku

        frame.add(buildRightPanel(), BorderLayout.EAST); // tworzymy prawy i dolny panel i dołączamy
        frame.add(buildBottomPanel(), BorderLayout.SOUTH);

        frame.setSize(1100, 750);
        frame.setVisible(true);
        updateButtons(); // ustawia widoczność przycisków (setEnabled)
    }

     /**
     * Builds the bottom status panel containing player and game state information.
     *
     * @return configured bottom panel
     */
    private JPanel buildBottomPanel() {
        JPanel bottom = new JPanel(new GridLayout(1, 4)); // 1 wiersz; 4 kolumny
        bottom.setBorder(new EmptyBorder(8, 8, 8, 8)); // margines 8px z każdej strony

        playerLabel = label("You are: ?");
        turnLabel = label("Waiting for game..."); // potem będzie aktualizowane na np. Your turn
        agreementLabel = label(" "); // na razie pusty label, zmienimy napis jak bedzie waitingForAgreement
        scoreLabel = label("P1: 0   P2: 0");

        bottom.add(playerLabel);
        bottom.add(turnLabel);
        bottom.add(agreementLabel);
        bottom.add(scoreLabel);
        return bottom;
    }

    /**
     * Builds the right panel containing control buttons and the console.
     *
     * @return configured right panel
     */
    private JPanel buildRightPanel() {
        JPanel right = new JPanel(new BorderLayout()); // layout N E W S / Center
        right.setPreferredSize(new Dimension(300, 600));

        // buttons
        JPanel buttons = new JPanel();
        buttons.setLayout(new GridLayout(0, 1, 5, 5)); // liczba wierszy, kolumn, px odstęp poziomy między elementami, odstęp pionowy między elementami (5 px)
        buttons.setBorder(new EmptyBorder(8, 8, 8, 8)); // margines 8px a kazdej strony

        passBtn = new JButton("PASS");
        resignBtn = new JButton("RESIGN");
        resumeBtn = new JButton("RESUME");
        finishBtn = new JButton("FINISH");

        passBtn.addActionListener(e -> conn.sendLine("PASS"));
        resignBtn.addActionListener(e -> conn.sendLine("RESIGN"));
        resumeBtn.addActionListener(e -> conn.sendLine("RESUME"));
        finishBtn.addActionListener(e -> conn.sendLine("FINISH"));

        buttons.add(passBtn);
        buttons.add(resignBtn);
        buttons.add(resumeBtn);
        buttons.add(finishBtn);

        // console
        console = new JTextPane();
        console.setEditable(false);
        console.setFont(new Font("Consolas", Font.PLAIN, 12));
        console.setBackground(new Color(20, 20, 20));
        console.setForeground(Color.LIGHT_GRAY);
        // consoleDoc jest typu StyledDocument
        consoleDoc = console.getStyledDocument(); //getStyledDocument() zwraca obiekt StyledDocument, który: pozwala na formatowanie tekstu (kolory, style, czcionki), będzie używany do wypisywania komunikatów w „konsoli” aplikacji.
        initConsoleStyles(); // w tej funkcji inicjalizujemy style (kolory dla różnych komunikatów)

        JScrollPane scroll = new JScrollPane(console); // Gdy dodajemy tekst do consoleDoc, zmienia się dokument JTextPane się odświeża, JScrollPane od razu to pokazuje

        right.add(buttons, BorderLayout.NORTH); // przyciski na gorze
        right.add(scroll, BorderLayout.CENTER); // konsola na srodku
        return right;
    }

    // pomocnicza funkcja używana w buildBottomPanel()
    /**
     * Helper method for creating centered labels with a uniform style.
     *
     * @param txt label text
     * @return configured JLabel
     */
    private JLabel label(String txt) {
        JLabel l = new JLabel(txt, SwingConstants.CENTER); // wyśrodkowanie tekstu w poziomie wewnątrz etykiety
        l.setFont(new Font("Arial", Font.BOLD, 14)); // czcionka
        return l;
    }

    /**
     * Updates button enabled states based on game status.
     */
    private void updateButtons() {
        passBtn.setEnabled(myTurn && !stoppedForAgreement);
        resignBtn.setEnabled(!stoppedForAgreement);
        resumeBtn.setEnabled(stoppedForAgreement);
        finishBtn.setEnabled(stoppedForAgreement);
    }

    // console
     /**
     * Initializes text styles used in the console.
     */
    private void initConsoleStyles() {
        addStyle("INFO", Color.LIGHT_GRAY);
        addStyle("ERROR", Color.RED);
        addStyle("SYSTEM", new Color(120, 180, 255));
    }

    /**
     * Adds a named text style to the console document.
     *
     * @param name style name
     * @param c text color
     */
    private void addStyle(String name, Color c) {
        Style s = console.addStyle(name, null);
        StyleConstants.setForeground(s, c);
    }

    // do wpisywania własnych logów z użyciem wcześniej zadeklarowanych stylów (kolorów)
    /**
     * Appends a styled log message to the console with a timestamp.
     *
     * @param type message type determining style
     * @param msg message content
     */
    private void log(String type, String msg) {
        SwingUtilities.invokeLater(() -> {
            try {
                String time = LocalTime.now().withNano(0).toString();
                consoleDoc.insertString(
                        consoleDoc.getLength(),
                        "[" + time + "] " + msg + "\n",
                        console.getStyle(type)
                );
                console.setCaretPosition(consoleDoc.getLength()); // Ustawiasz kursor na sam dół tekstu; przewija scrollPane na dół
            } catch (BadLocationException ignored) {}
        });
    }

    // handlers
    /**
     * Registers handlers for messages received from the server.
     */
    private void registerHandlers() {
        conn.startListening(new ClientConnection.MessageHandler() {
            @Override public void onStart(int id) {
                myId = id;
                String color = (id == 1 ? "Black" : "White");
                playerLabel.setText("You are Player " + id + " (" + color + ")");
                log("SYSTEM", "You are Player " + id + " (" + color + ")");
            }

            @Override public void onBoard(Board b) {
                board = b;
                boardPanel.repaint(); // wywołuje PaintComponent
            }

            @Override public void onYourTurn() {
                myTurn = true;
                if (!stoppedForAgreement) {
                    turnLabel.setText("Your turn");
                    log("SYSTEM", "Your turn");
                }
                updateButtons();
            }

            @Override public void onOpponentTurn() {
                myTurn = false;
                if (!stoppedForAgreement) {
                    turnLabel.setText("Opponent's turn");
                    log("SYSTEM", "Opponent's turn");
                }
                updateButtons();
            }

            @Override public void onInfo(String msg) {
                log("INFO", msg);
            }

            @Override public void onError(String msg) {
                log("ERROR", msg);
            }

            @Override public void onGameOver(String msg) {
                JOptionPane.showMessageDialog(frame, msg, "Game Over", JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
            }

            @Override public void onDisconnect() {
                JOptionPane.showMessageDialog(frame, "Disconnected");
                System.exit(0);
            }

            @Override public void onUnknown(String line) {
                log("SYSTEM", line);
            }

            @Override public void onstoppedForAgreement() {
                stoppedForAgreement = true;
                turnLabel.setText(" ");
                agreementLabel.setText("STOPPED FOR AGREEMENT");
                log("SYSTEM", "Game stopped for agreement");
                updateButtons();
            }

            @Override public void offstoppedForAgreement() {
                stoppedForAgreement = false;
                agreementLabel.setText(" ");
                log("SYSTEM", "Game resumed");
                updateButtons();
            }

            @Override public void wynikiPierwszego(int a) {
                wyniki[0] = a;
                scoreLabel.setText("P1: " + wyniki[0] + "   P2: " + wyniki[1]);
            }

            @Override public void wynikiDrugiego(int a) {
                wyniki[1] = a;
                scoreLabel.setText("P1: " + wyniki[0] + "   P2: " + wyniki[1]);
            }
        });
    }

    // BoardPanel !!!
    /**
     * Custom panel responsible for rendering the game board
     * and handling mouse input.
     */
    private class BoardPanel extends JPanel {
        /** Margin from panel edges to the board grid */
        private static final int M = 40; // margines planszy od krawędzi

        /**
         * Creates the board panel and registers mouse input handling.
         */
        BoardPanel() {
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (board == null) return;

                    if (stoppedForAgreement) {
                        log("INFO", "Game is stopped for agreement");
                        return;
                    }

                    if (!myTurn) {
                        log("INFO", "Not your turn");
                        return;
                    }

                    int size = board.size; //rozmiar boku planszy np. 19
                    int cell = Math.min(getWidth(), getHeight()) / (size + 1); // (getWidth() zwraca szerokość komponentu w px (boardPanel), mniejszy wymiar dzielimy na size+1. UWAGA dla size=19 mamy 18 komórek w boku!!! więc to działa względnie dobrze
                    int c = Math.round((e.getX() - M) / (float) cell); // pozycja klikniecia na rozmiar komorki z przesunieciem o margines
                    int r = Math.round((e.getY() - M) / (float) cell);
                    if (r < 0 || c < 0 || r >= size || c >= size) return; // poza planszą klik
                    conn.sendMoveJson(JsonUtil.moveToJson(new Move(r, c, myId))); // wysylamy move kliknięciem
                }
            });
        }

        /**
         * Renders the board grid and stones.
         *
         * @param g graphics context
         */
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (board == null) return;

            Graphics2D g2 = (Graphics2D) g; // Graphics2D pozwala na bardziej zaawansowane rysowanie niż zwykły Graphics
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // Antyaliasing sprawia, że linie i okręgi są wygładzone

            int size = board.size;
            int cell = Math.min(getWidth(), getHeight()) / (size + 1); // rozmiar pojedynczej komorki w px

            g2.setColor(new Color(220, 180, 120)); // kolor tła
            g2.fillRect(0, 0, getWidth(), getHeight()); // wypełniamy nim cały BoardPanel

            //rysowanie planszy ( czarnych linii)
            g2.setColor(Color.BLACK);
            for (int i = 0; i < size; i++) {
                int p = M + i * cell; // margines + przesuniecie
                g2.drawLine(M, p, M + cell * (size - 1), p); // poziome; od (M, p) do (M + cell*(size-1), p)
                g2.drawLine(p, M, p, M + cell * (size - 1)); // pionowe; od (p, M) do (p, M + cell*(size-1))
            }
            //rysowanie kamieni
            int rStone = cell / 2 - 2; // promień kamienia
            for (int r = 0; r < size; r++) for (int c = 0; c < size; c++) {
                int v = board.grid[r][c];
                if (v == 0) continue; // puste pole
                int x = M + c * cell; // wspolrzedne do wstawienia kamienia
                int y = M + r * cell;
                g2.setColor(v == 1 ? Color.BLACK : Color.WHITE); // kolor zalezny czy 1 czy nie
                g2.fillOval(x - rStone, y - rStone, rStone * 2, rStone * 2); // fillOval rysuje wypełniony kamień
                g2.setColor(Color.BLACK);
                g2.drawOval(x - rStone, y - rStone, rStone * 2, rStone * 2); // drawOval rysuje obwódkę
            }
        }
    }
}