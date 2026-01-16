package lab4.database;

import jakarta.persistence.*; // Importuje wszystkie niezbędne adnotacje (@Entity, @Id itp.).
import java.time.LocalDateTime; // Importuje typ daty i czasu.
import java.util.ArrayList;
import java.util.List;
// "Tabela gier", encja. Klasa ta jest instrukcją dla bazy danych, jak ma wyglądać tabela przechowująca informacje o całych rozgrywkach.
// klasa tworzy tabelę GAMES z kolumnami: ID, RESULT, START_TIME (spring zmienia nazwy zmiennych z camelcase na takie nazwy z _ )
// kazda zmienna z klasy dostanie kolumne

@Entity // Na podstawie tej klasy stwórz tabelę w bazie danych. Mówi Springowi "Ta klasa to tabela w bazie danych". Bez tego to zwykła klasa.
@Table(name = "games") // nazywa tabele.  Bez tego nazwałby ją 'game_entity'.
public class GameEntity {
    @Id // to pole to klucz główny
    @GeneratedValue(strategy = GenerationType.IDENTITY) // IDENTITY - Baza danych (H2) posiada wewnętrzny licznik, tzw. AUTO_INCREMENT
    private Long id; // (Long musi byc oczywiscie tez w GameRepository)

    // jak chcielibysmy dac wlasna nazwe kolumny w bazie to mozemy dac np:
    // @Column(name = "kiedy_zagrano")
    private LocalDateTime startTime; // kolumna w bazie "start_time". Data rozpoczęcia.
    private String result; // Kolumna w bazie "result". Np. "Player 1 wins".

    // jedna gra (Game) ma wiele ruchów (Moves) (to opis arraylista ponizej, nie trafi ta zmienna do bazy)
    // mappedBy = "game": Wskazuje, że powiązanie bieżącej klasy (ONE) jest opisane w drugiej klasie (MoveEntity) w polu "game" (ich jest MANY). nasze ONE wezime prawdopodobnie z klucza @ID
    // gameRepository.save(game)), to dzięki Cascade ALL przy usueniciu gry samo usuwa wszystkie ruchy; baza automatycznie zapisałaby też wszystkie te ruchy. (W naszym kodzie akurat zapisujemy ruchy osobno, ale to zabezpieczenie jest dobrą praktyką).
    // fetch - Gdy pobierasz z bazy grę, od razu pobierz też listę wszystkich jej ruchów
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<MoveEntity> moves = new ArrayList<>();

    public GameEntity() {
        this.startTime = LocalDateTime.now(); // Przy tworzeniu gry, automatycznie ustawiamy czas na "teraz".
    }

    // Gettery i settery - pozwalają pobierać i zmieniać wartości pól
    public Long getId() { return id; }
    public void setResult(String result) { this.result = result; }
    public String getResult() { return result; }
    public List<MoveEntity> getMoves() { return moves; }
    public void setMoves(List<MoveEntity> moves) { this.moves = moves; }
}