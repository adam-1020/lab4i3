package lab4.database;

import org.springframework.data.jpa.repository.JpaRepository; //  Importuje "Gotowca" ze Springa.
import org.springframework.stereotype.Repository; // Oznacza: "To jest komponent zarządzający danymi"

@Repository // Mówi Springowi: "Zarejestruj to jako Repozytorium, żebym mógł używać @Autowired". (w serverMain)
public interface GameRepository extends JpaRepository<GameEntity, Long> {
} //zarządza tabela gier (GameEntity); Klucz główny (ID) w tej encji jest typu Long

//Do czego sluży @Repository: !!!
//Abstrakcja: Nie interesuje Cię, czy pod spodem jest baza H2, MySQL czy PostgreSQL. Tylko wywolujemy save() i to działa.
//
//Gotowe metody:
//save(entity) – Zapisz/Aktualizuj.
//findById(id) – Znajdź po ID.
//findAll() – Daj mi wszystko co masz.
//count() – Ile tego jest?
//delete(entity) – Usuń.