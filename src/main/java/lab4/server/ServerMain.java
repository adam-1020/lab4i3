package lab4.server;

import lab4.database.GameRepository;
import lab4.database.MoveRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Server: listens for connections and delegates them to GameSession.
 * Uruchamiany przez Spring Boot.
 */
@SpringBootApplication
@ComponentScan(basePackages = "lab4")
@EntityScan(basePackages = "lab4.database")
@EnableJpaRepositories(basePackages = "lab4.database") // Java Persistence API
//CommandLineRunner W Spring Boot oznacza, że kod w metodzie run() uruchomi się automatycznie po starcie aplikacji
public class ServerMain implements CommandLineRunner
{
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private MoveRepository moveRepository;

    public static void main(String[] args)
    {
        SpringApplication.run(ServerMain.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        final int port = 55555;
        final int boardSize = 19; //tu bedzie mozna zmienic rozmiar planszy

        System.out.println("Server starting on port " + port + " (board " + boardSize + "x" + boardSize + ")");

        // Inicjalizacja Singletona z repozytoriami
        GameSession session = GameSession.getInstance(boardSize);
        session.setRepositories(gameRepository, moveRepository);

        // Osobny wątek do nasłuchiwania graczy w pętli nieskończonej
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port))
            {
                System.out.println("Waiting for players...");

                while (true) // Petla nieskonczona - serwer dziala caly czas
                {
                    try {
                        Socket client = serverSocket.accept();
                        // Delegujemy dodanie gracza do GameSession
                        // To GameSession decyduje czy przyjąć (czy jest miejsce) i kiedy zacząć grę.
                        // zmieniam to dlatego, zeby moc obslugiwac wiele gier z rzedu !!!!
                        GameSession.getInstance().tryAddPlayer(client);
                    } catch (IOException e) {
                        System.err.println("Accept failed: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Menu konsolowe serwera
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("=== SERVER CONSOLE ===");
        System.out.println("Commands:");
        System.out.println("  bot       -> Add a bot player");
        System.out.println("  replay ID -> Replay game with ID");
        System.out.println("  reset     -> Force reset session");
        System.out.println("  exit      -> Stop server");

        while (true) {
            String line = reader.readLine();
            if (line == null) break;

            String cmd = line.trim();
            if (cmd.equalsIgnoreCase("exit")) {
                System.exit(0);
            }
            else if (cmd.equalsIgnoreCase("bot")) {
                System.out.println("Starting bot...");
                new Thread(new SimpleBot()).start();
            }
            else if (cmd.equalsIgnoreCase("reset")) {
                GameSession.getInstance().reset();
            }
            else if (cmd.toLowerCase().startsWith("replay ")) {
                try {
                    long id = Long.parseLong(cmd.split(" ")[1]);
                    GameSession.getInstance().replayGameFromDb(id);
                } catch (Exception e) {
                    System.out.println("Invalid replay format. Use: replay <ID>");
                }
            }
        }
    }
}