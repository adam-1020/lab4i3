package lab4.common;

/**
 * DTO (Data Transfer Object) — Move: przesylamy jako JSON: {"row":R,"col":C,"player":P}
 */
public class Move {
    /** Row index of the move */
    public int row;
    /** Column index of the move */
    public int col;
    /** Player making the move (1 or 2) */
    public int player;

    /**
     * Default constructor for creating an empty Move
     * (useful for deserialization from JSON).
     */
    public Move() {} // zeby moc stworzyc Move i potem dopisac dane z JSON-a

    /**
     * Creates a Move with specified row, column, and player.
     *
     * @param row row index
     * @param col column index
     * @param player player ID (1 or 2)
     */
    public Move(int row, int col, int player) {
        this.row = row;
        this.col = col;
        this.player = player;
    }

    //do ewentualnej pomocy przy printach
    /**
     * Returns a human-readable string representation of the move.
     *
     * @return string in the format Move[player=X, row=R, col=C]
     */
    @Override
    public String toString() {
        return "Move[player=" + player + ", row=" + row + ", col=" + col + "]";
    }
}