package lab4.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilTest {

    @Test
    void testMoveRoundtrip() {
        Move m = new Move(2, 3, 1);
        String json = JsonUtil.moveToJson(m);
        Move parsed = JsonUtil.jsonToMove(json);

        assertEquals(m.row, parsed.row);
        assertEquals(m.col, parsed.col);
        assertEquals(m.player, parsed.player);
    }

    /* Lambda jest użyta, ponieważ assertThrows oczekuje obiektu typu Executable, czyli fragmentu kodu
    do wykonania. Dzięki lambdzie JUnit sam uruchamia metodę i może przechwycić oraz zweryfikować rzucony wyjątek*/
    @Test
    void testMoveInvalidJsonThrows() {
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.jsonToMove("this is not json"));
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.jsonToMove("{}"));
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.jsonToMove("{\"row\":1}"));
    }

    @Test
    void testBoardRoundtrip() {
        Board b = new Board(3);
        b.grid[0][0] = 1;
        b.grid[1][2] = 2;
        String json = JsonUtil.boardToJson(b);

        Board parsed = JsonUtil.jsonToBoard(json);
        assertEquals(b.size, parsed.size);
        assertTrue(Board.gridsEqual(b.grid, parsed.grid), "Plansza po serializacji i deserializacji powinna być równa");
    }

    @Test
    void testBoardInvalidJsonThrows() {
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.jsonToBoard("not a board"));
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.jsonToBoard("{\"size\":3}")); // brak grid
    }
}