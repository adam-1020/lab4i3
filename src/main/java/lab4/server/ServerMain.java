package lab4.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Server: accepts exactly two clients, registers them in GameSession and starts the game.
 */
public class ServerMain
{
    public static void main(String[] args)
    {
        final int port = 55555;
        final int boardSize = 19; //tu bedzie mozna zmienic rozmiar planszy

        System.out.println("Server starting on port " + port + " (board " + boardSize + "x" + boardSize + ")");

        try (ServerSocket serverSocket = new ServerSocket(port))
        {

            // Prepare SINGLETON session
            GameSession.getInstance(boardSize);

            int connected = 0;
            ClientHandler[] handlers = new ClientHandler[2]; //tablica z dwoma handlerami dla playerow; do komunikacji z klientem

            while (connected < 2)
            {
                Socket client = serverSocket.accept(); // serverSocket.accept() blokuje wątek, aż klient się połączy
                connected++;
                System.out.println("Client connected - assign playerId=" + connected);
                ClientHandler handler = new ClientHandler(client, connected); // tworzymy clientHandler podajac do konstruktora socket ktory zaakceptowalismy wlasnie
                handlers[connected-1] = handler;
                GameSession.getInstance().register(handler); //Rejestruje handler w sesji gry, żeby gra wiedziała o wszystkich graczach
                new Thread(handler, "ClientHandler-" + connected).start(); //Tworzy nowy wątek dla klienta, żeby obsługa komunikacji była równoległa
            }

            System.out.println("Two players connected. Starting game.");
            GameSession.getInstance().startGame();

            // keep server alive (ale tylko dopoki nie jest gameOver)
            while (GameSession.getInstance().isRunning()) Thread.sleep(1000);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}