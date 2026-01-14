package lab4.common;

import java.util.Stack;

/**
 * DTO + logika planszy (capture, suicide check).
 *
 * Uwaga: GameSession zarządza turą, KO, passami i obserwatorami.
 */
public class Board {
    /** Board size (number of rows and columns) */
    public final int size;
     /**
     * Board grid representation:
     * 0 - empty field,
     * 1 - player 1 (X),
     * 2 - player 2 (O)
     */
    public int[][] grid; // 0 empty, 1 player1 (X), 2 player2 (O) !!!

    /**
     * Creates an empty board of the given size.
     *
     * @param size board dimension
     */
    public Board(int size) {
        this.size = size;
        this.grid = new int[size][size];
    }

    /**
     * Checks whether given coordinates are within board bounds.
     *
     * @param r row index
     * @param c column index
     * @return true if coordinates are inside the board
     */
    private boolean inBounds(int r, int c) {
        return r >= 0 && c >= 0 && r < size && c < size;
    }

    /**
     * Checks whether a given board position is empty.
     *
     * @param r row index
     * @param c column index
     * @return true if the position is empty and within bounds
     */
    public synchronized boolean isEmpty(int r, int c) {
        return inBounds(r, c) && grid[r][c] == 0;
    }

    /**
     * Applies a move to the board and performs captures if applicable.
     * <p>
     * Return values:
     * <ul>
     *   <li>-1 – field occupied or out of bounds</li>
     *   <li>-2 – suicide move (illegal)</li>
     *   <li>&gt;= 0 – number of captured enemy stones</li>
     * </ul>
     * <p>
     * The board state is modified and the method is synchronized.
     *
     * @param r row index
     * @param c column index
     * @param player player identifier (1 or 2)
     * @return result code or number of captured stones
     */
    public synchronized int applyMoveAndCapture(int r, int c, int player) {
        if (!inBounds(r, c)) return -1;
        if (grid[r][c] != 0) return -1; //czyli jest empty

        // place temporarily
        grid[r][c] = player;

        int enemy = (player == 1 ? 2 : 1);
        int captured = 0;

        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}}; // do iteracji po kierunkach na boki, gora i dol
        // check neighbor enemy groups for capture
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            if (!inBounds(nr,nc)) continue;
            if (grid[nr][nc] == enemy) {
                if (!hasLiberties(grid, nr, nc)) { // sprawdzamy czy jakis wrogi sasiad nie ma teraz 0 oddechow (umieramy go)
                    captured += removeGroup(nr, nc, enemy); // dostajemy punkty; usuwamy zlepek kulek wroga
                }
            }
        }

        // check if placed group has liberties now
        if (!hasLiberties(grid, r, c)) {
            if (captured == 0) {
                // suicide -> revert
                grid[r][c] = 0;  // zerujemy, czyli pole pozostaje puste
                return -2;
            }
            // else: if captured > 0, the move can be legal (captures freed liberties)
        }

        return captured;
    }

    // removes group of 'color' starting at r,c and returns how many stones removed
    /**
     * Removes a connected group of stones of the given color.
     *
     * @param r starting row
     * @param c starting column
     * @param color stone color to remove
     * @return number of removed stones
     */
    private int removeGroup(int r, int c, int color) {
        if (!inBounds(r,c)) return 0;
        if (grid[r][c] != color) return 0;

        int removed = 0;
        Stack<int[]> st = new Stack<>();
        st.push(new int[]{r,c}); // opis w funkcji nizej
        // mark visited by negating color (temporary marker); zeby nie wpasc w petle z tymi ktore juz zaznaczylismy
        grid[r][c] = -color;

        while (!st.isEmpty()) {
            int[] p = st.pop();
            int pr = p[0], pc = p[1];
            removed++;
            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
            for (int[] d : dirs) {
                int nr = pr + d[0], nc = pc + d[1];
                if (!inBounds(nr,nc)) continue;
                if (grid[nr][nc] == color) {
                    st.push(new int[]{nr,nc});
                    grid[nr][nc] = -color;
                }
            }
        }

        // clear markers -> set to 0 (removed)
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (grid[i][j] == -color) grid[i][j] = 0;
            }
        }

        return removed;
    }

    // check if group at r,c has liberties on the given board array
    // does not modify boardCopy
    /**
     * Checks whether a connected group at the given position
     * has at least one liberty.
     * <p>
     * This method does not modify the board.
     *
     * @param boardCopy board state to analyze
     * @param r row index
     * @param c column index
     * @return true if the group has at least one liberty
     */
    private boolean hasLiberties(int[][] boardCopy, int r, int c) {
        if (!inBounds(r,c)) return false;
        int color = boardCopy[r][c];
        if (color == 0) return true;

        boolean[][] visited = new boolean[size][size];
        Stack<int[]> st = new Stack<>();
        st.push(new int[]{r,c}); // wrzucamy pierwszy kamien
        visited[r][c] = true; // odwiedzony

        while (!st.isEmpty()) {
            int[] p = st.pop();
            int pr = p[0], pc = p[1];
            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}}; // cztery kierunki
            for (int[] d : dirs) {
                int nr = pr + d[0], nc = pc + d[1];
                if (!inBounds(nr,nc)) continue;
                if (boardCopy[nr][nc] == 0) return true; // znalezlismy wolne pole, czyli ma jakis oodech
                if (!visited[nr][nc] && boardCopy[nr][nc] == color) {
                    visited[nr][nc] = true;
                    st.push(new int[]{nr,nc});
                }
            }
        }
        return false;
    }

    // returns deep copy of grid
    /**
     * Returns a deep copy of the board grid.
     *
     * @return copied grid array
     */
    public synchronized int[][] getGridCopy() {
        int[][] copy = new int[size][size];
        for (int i = 0; i < size; i++) System.arraycopy(grid[i], 0, copy[i], 0, size);
        return copy;
    }

    // restore from copy
    /**
     * Restores the board state from a given grid copy.
     *
     * @param src source grid
     */
    public synchronized void setGridFromCopy(int[][] src) {
        if (src == null || src.length != size) return;
        for (int i = 0; i < size; i++) System.arraycopy(src[i], 0, grid[i], 0, size);
    }

    // static compare
    /**
     * Compares two board grids for equality.
     *
     * @param a first grid
     * @param b second grid
     * @return true if grids are equal
     */
    public static boolean gridsEqual(int[][] a, int[][] b) {
        if (a == null || b == null) return false;
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a.length; j++) {
                if (a[i][j] != b[i][j]) return false;
            }
        }
        return true;
    }

    /**
     * Returns a human-readable textual representation of the board.
     *
     * @return board as formatted string
     */
    @Override
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("    ");
        for (int c = 0; c < size; c++) sb.append(String.format("%2d", c));
        sb.append("\n");
        for (int r = 0; r < size; r++) {
            sb.append(String.format("%2d: ", r));
            for (int c = 0; c < size; c++) {
                char ch = '.';
                if (grid[r][c] == 1) ch = 'X';
                if (grid[r][c] == 2) ch = 'O';
                sb.append(" ").append(ch);
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}