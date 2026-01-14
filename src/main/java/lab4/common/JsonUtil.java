package lab4.common;

/**
 * Prosta, reczna serializacja/deserializacja JSON dla Move i Board
 * Dziala z formatem ktory uzywamy w komunikacji (nie jest to pelny JSON parser)
 */
public class JsonUtil {

    /**
     * Serializes a Move object into a JSON string.
     *
     * @param m move to serialize
     * @return JSON representation of the move
     */
    public static String moveToJson(Move m) {
        return "{\"row\":" + m.row + ",\"col\":" + m.col + ",\"player\":" + m.player + "}";
    } // \" to tzw. escape sequence, czyli sposób na zapisanie znaku, który normalnie miałby specjalne znaczenie w kodzie

    /**
     * Deserializes a JSON string into a Move object.
     *
     * @param json JSON string representing a move
     * @return Move object
     * @throws IllegalArgumentException if the JSON is invalid or fields are missing
     */
    public static Move jsonToMove(String json) {
        try {
            String s = json.trim().replaceAll("[{}\" ]", "");
            String[] parts = s.split(",");
            int row = -1, col = -1, player = -1;
            for (String p : parts) {
                if (p.startsWith("row:")) row = Integer.parseInt(p.substring(4)); //czesc indeksu od 4 do konca
                if (p.startsWith("col:")) col = Integer.parseInt(p.substring(4));
                if (p.startsWith("player:")) player = Integer.parseInt(p.substring(7));
            }
            if (row < 0 || col < 0 || player <= 0) throw new IllegalArgumentException("Bad move fields");
            return new Move(row, col, player);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Move JSON: " + e.getMessage());
        }
    }

    /**
     * Serializes a Board object into a JSON string.
     *
     * @param b board to serialize
     * @return JSON representation of the board
     */
    public static String boardToJson(Board b) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"size\":").append(b.size).append(",\"grid\":[");
        for (int r = 0; r < b.size; r++) {
            sb.append("[");
            for (int c = 0; c < b.size; c++) {
                sb.append(b.grid[r][c]);
                if (c < b.size - 1) sb.append(",");
            }
            sb.append("]");
            if (r < b.size - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

     /**
     * Deserializes a JSON string into a Board object.
     *
     * @param json JSON string representing a board
     * @return Board object
     * @throws IllegalArgumentException if the JSON is invalid or missing required fields
     */
    public static Board jsonToBoard(String json) {
        try {
            String s = json.replace("\n", "");
            int sizePos = s.indexOf("\"size\":"); //index z size
            if (sizePos < 0) throw new IllegalArgumentException("size missing");
            int comma = s.indexOf(",", sizePos); //pierwszy przecinek za sizePos
            int size = Integer.parseInt(s.substring(sizePos + 7, comma).trim()); //s i z e : " czyli 7 pozycja za

            int gridStart = s.indexOf("[", s.indexOf("\"grid\":"));
            int gridEnd = s.lastIndexOf("]");
            String gridContent = s.substring(gridStart + 1, gridEnd);
            // split po [ i ]
            String[] rows = gridContent.split("\\],\\["); // W Javie backslash musi być podwójny, więc zapisujemy \\] i \\[
            Board b = new Board(size);
            for (int r = 0; r < size; r++) {
                String row = rows[r].replace("[","").replace("]",""); //oczyszczamy
                String[] vals = row.split(","); // poszczegolne wartosci w wierszu
                for (int c = 0; c < size; c++) { // iteracja po kolumnach
                    b.grid[r][c] = Integer.parseInt(vals[c].trim());
                }
            }
            return b;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Board JSON: " + e.getMessage());
        }
    }
}