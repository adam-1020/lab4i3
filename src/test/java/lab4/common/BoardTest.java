package lab4.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardTest {

    @Test
    void testApplyMoveCapturesSingleStone() {
        Board b = new Board(3);

        // ustawienie: przeciwnik (2) ma pojedynczy kamień na (1,1)
        b.grid[1][1] = 2;

        // gracz 1 ma trzy kamienie otaczające go z trzech stron; wolne pole (2,1) -> tam zagramy i zbierzemy kamień (1,1)
        b.grid[0][1] = 1;
        b.grid[1][0] = 1;
        b.grid[1][2] = 1;

        int result = b.applyMoveAndCapture(2, 1, 1);

        assertEquals(1, result, "Powinien zostać złapany dokładnie 1 kamień");
        assertEquals(0, b.grid[1][1], "Złapany kamień powinien zostać usunięty");
        assertEquals(1, b.grid[2][1], "Postawiony kamień powinien zostać na miejscu");
    }

    @Test
    void testApplyMoveSuicide() {
        Board b = new Board(3);

        // wokół pola (1,1) wszystkie cztery są zajęte przez przeciwnika -> ruch w (1,1) to samobójstwo
        b.grid[0][1] = 2;
        b.grid[1][0] = 2;
        b.grid[1][2] = 2;
        b.grid[2][1] = 2;

        int result = b.applyMoveAndCapture(1, 1, 1);
        assertEquals(-2, result, "Ruch samobójczy zwraca -2");
        assertEquals(0, b.grid[1][1], "Pole powinno pozostać puste po odrzuceniu ruchu");
    }

    @Test
    void testGetGridCopyAndSetAndGridsEqual() {
        Board b = new Board(4);
        b.grid[0][0] = 1;
        b.grid[3][3] = 2;

        int[][] copy = b.getGridCopy();
        assertTrue(Board.gridsEqual(copy, b.getGridCopy()), "Kopia powinna być równa obecnemu gridowi");

        // zmieniamy kopię i przywracamy przez setGridFromCopy
        copy[0][0] = 0;
        b.setGridFromCopy(copy);
        assertEquals(0, b.grid[0][0], "setGridFromCopy powinno przywrócić zmiany z kopii");
    }

    @Test
    void testIsEmptyAndBounds() {
        Board b = new Board(3);
        assertFalse(b.isEmpty(-1, 0), "Poza planszą -> false");
        assertFalse(b.isEmpty(0, -1), "Poza planszą -> false");
        assertFalse(b.isEmpty(3, 0), "Poza planszą -> false");
        assertTrue(b.isEmpty(0, 0), "Puste pole -> true");

        b.grid[0][0] = 1;
        assertFalse(b.isEmpty(0, 0), "Zajęte pole -> false");
    }
}