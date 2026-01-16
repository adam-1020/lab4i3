package lab4.database;

import jakarta.persistence.*; // Import adnotacji bazy danych

@Entity // To jest tabela w bazie danych
@Table(name = "moves") // nazwa
public class MoveEntity {
    @Id // klucz glowny ruchu
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto increment
    private Long id;

    private int moveOrder; // kolejnosc ruchu. Który to był ruch w kolejności (1, 2, 3...).
    private int rowInd;    // row
    private int colInd;    // col
    private int playerId;  // id gracza co zagral ruch

    // druga strona relacji - w tym miejscu joinColumn
    @ManyToOne // Wiele ruchów należy do jednej gry
    @JoinColumn(name = "game_id") // Stwórz w tej tabeli kolumnę 'game_id', która będzie trzymać ID gry. WAZNE !!!
    private GameEntity game; // Obiekt Javy, dzięki któremu ruch "wie", do jakiej gry należy

    public MoveEntity() {} // Pusty konstruktor (wymagany przez bazę),np. gdy Spring chce pobrać dane z bazy i zamienić je w obiekt Java (np. przy replayGameFromDb)

    //konstruktor, którego używasz w GameSession do szybkiego tworzenia ruchu.
    public MoveEntity(int moveOrder, int row, int col, int playerId, GameEntity game) {
        this.moveOrder = moveOrder;
        this.rowInd = row;
        this.colInd = col;
        this.playerId = playerId;
        this.game = game; // Tu przypisujesz ten ruch do konkretnej gry
    }

    // Gettery (żeby GameSession mogło odczytać dane przy Replayu)
    public int getRowInd() { return rowInd; }
    public int getColInd() { return colInd; }
    public int getPlayerId() { return playerId; }
}