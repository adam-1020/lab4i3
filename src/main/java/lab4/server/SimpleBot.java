package lab4.server;

import lab4.common.Board;
import lab4.common.JsonUtil;
import lab4.common.Move;

import java.io.*;
import java.net.Socket;
import java.util.Random;

/**
 * Prosty bot działający jako klient na serwerze.
 * Łączy się do localhost i wykonuje poprawne ruchy (z uwzględnieniem KO i prostej taktyki).
 * (algorytm jeszcze do zaimplementowania)
 */
public class SimpleBot implements Runnable {

    private final String host = "localhost";
    private final int port = 55555;

    private int myId;

    @Override
    public void run() {

    }

    private void makeMove(PrintWriter out) {
        try { Thread.sleep(700); } catch (InterruptedException ignored) {}

    }

    private boolean isEdge(int coord, int size) {
        return coord == 0 || coord == size - 1;
    }

    private void sendMove(PrintWriter out, int r, int c) {
        Move m = new Move(r, c, myId);
        String json = JsonUtil.moveToJson(m);
        out.println("MOVE " + json);
        System.out.println("[BOT"+myId+"] Sent move: " + r + ", " + c);
    }
}