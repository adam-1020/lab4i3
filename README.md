src/

├─ client/

│    ├─ ClientMain.java

│    ├─ SwingClientMain.java

│    └─ ClientConnection.java

├─ server/

│    ├─ ServerMain.java

│    ├─ ClientHandler.java

│    └─ GameSession.java

└─ common/

│    ├─ Board.java

│    ├─ Move.java

│    └─ JsonUtil.java

Wzorce:

Singleton – GameSession (jedna gra).

DTO – Move, Board → to co idzie w JSON.

Observer (wersja prymitywna) – serwer powiadamia dwóch klientów o zmianie plans

Instrukcja uruchomienia:

w src\main\java:

`javac .\lab4\client\*.java`

`javac .\lab4\server\*.java`

nastęnie odpalamy serwer:

java lab4.server.ServerMain

i odpalamy dwóch klientów(graczy) używając SwingClientMain lub ClientMain:

java lab4.client.ClientMain

java lab4.client.SwingClientMain

Opis:

plansza 19×19 (do ew zmiany w ServerMain),

Singleton: GameSession (jedna gra),

DTO (Data Transfer Object): Move, Board (wysyłane w JSON),

Observer (prymitywny): GameSession powiadamia ClientHandler o zmianach planszy,

INSTRUKCJA:

PASS (pominięcie ruchu),

SCORE (wyswietla punkty za zbite kamienie dla obu graczy),

RESIGN (poddanie się),

EXIT/QUIT (rozłączenie się),

po 2x PASS:

RESUME (wznawia gre z ruchem dla przeciwnika)

FINISH (jak obaj gracze się zgodzą to koniec gry)

FUNKCJONALNOŚCI:

zdejmowanie (capturing) grup przeciwnika (grupy mają wspólne oddechy),

blokada ruchu samobójczego (suicide),

blokada KO (zabronione natychmiastowe powtórzenie pozycji — porównanie z pozycją sprzed ostatniego ruchu),

interfejs konsolowy, który pokazuje planszę i komunikaty,

obsługa błędów i rozłączeń,

Serwer akceptuje dokładnie 2 połączenia i potem uruchamia grę.

Capture: zadziała dla otoczonych grup (rekursywnie / stack).

Po 2x PASS stoppedForAgreement=true. Wtedy albo gracze się zgadzają i gra się kończy, albo nie - wtedy kontynuują.

Sposób działania komunikacji: (!!!)

Klient → serwer

ClientConnection wysyla np. obiekt Move zamieniony na JSON w JsonUtil; odbiera ClientHandler

Serwer → klient

wysyła ClientHandler (lub GameSession za jego pomocą); odbiera ClientConnection

DODATKOWE RZECZY W I2:

boolean stoppedForAgreement i wyniki przechowywane na biezaco w ClientMain/SwingClientMain. Do wykorzystania później w GUI.