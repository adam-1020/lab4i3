package lab4.server;

import lab4.common.Board;
import lab4.common.Move;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class GameSessionLogicTest {

    @BeforeEach // za pomocą refleksji (Field) zmieniamy prywatne pole static w klasie GameSession
    void resetSingleton() throws Exception { // resetuje Singleton GameSession przed każdym testem
        Field field = GameSession.class.getDeclaredField("instance"); // Pobieramy prywatne pole statyczne; field to obiekt typu Field
        field.setAccessible(true); // Java normalnie zabrania dostępu do private, Reflection pozwala to obejść
        field.set(null, null); // pierwsze null -> obiekt (bo pole jest static, nie ma instancji); drugie null -> nowa wartość pola
    } // czyli: GameSession.instance = null;

    @Test
    void gameStartsWithEmptyBoard() throws Exception {  // throws Exception łapie NoSuchFieldException i IllegalAccessException
        GameSession gs = GameSession.getInstance(9);

        Field boardField = GameSession.class.getDeclaredField("board");
        boardField.setAccessible(true);
        Board b = (Board) boardField.get(gs); // get(gs) zwraca aktualną wartość pola dla danego obiektu gs

        assertEquals(9, b.size);
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++)
                assertEquals(0, b.grid[r][c]);
    }

    @Test
    void applyingMoveThroughGameSessionChangesBoard() throws Exception {
        GameSession gs = GameSession.getInstance(5);

        Field boardField = GameSession.class.getDeclaredField("board");
        boardField.setAccessible(true);
        Board board = (Board) boardField.get(gs);

        int result = board.applyMoveAndCapture(2, 2, 1);

        assertEquals(0, result);  // brak zbitych kamieni
        assertEquals(1, board.grid[2][2]);  // ruch został wykonany
    }

    @Test
    void suicideMoveThroughGameSessionIsRejected() throws Exception {
        GameSession gs = GameSession.getInstance(5);

        Field boardField = GameSession.class.getDeclaredField("board");
        boardField.setAccessible(true);
        Board board = (Board) boardField.get(gs);

        // ustawienie otoczenia, aby ruch w (2,2) był samobójczy
        board.grid[1][2] = 2;
        board.grid[2][1] = 2;
        board.grid[2][3] = 2;
        board.grid[3][2] = 2;

        int result = board.applyMoveAndCapture(2, 2, 1);

        assertEquals(-2, result); // nie pozwala na suicide
        assertEquals(0, board.grid[2][2]); // przeciecie pozostaje puste
    }
}