package ar.edu.is2.scouting.config;

import ar.edu.is2.scouting.domain.player.Position;
import ar.edu.is2.scouting.domain.user.UserAccountService;
import ar.edu.is2.scouting.domain.user.UserRole;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoDataInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final UserAccountService users;

    public DemoDataInitializer(JdbcTemplate jdbc, UserAccountService users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUser("Director Técnico", "dt@demo.local", UserRole.DIRECTOR);
        seedUser("Coordinación de Scouting", "coordinador@demo.local", UserRole.COORDINATOR);
        seedUser("Scout Demo", "scout@demo.local", UserRole.SCOUT);
        if (jdbc.queryForObject("select count(*) from player", Long.class) == 0) seedFootballData();
        seedAdditionalPlayers();
    }

    private void seedUser(String name, String email, UserRole role) {
        if (jdbc.queryForObject("select count(*) from user_account where email=?", Long.class, email) == 0) {
            users.register(name, email, "demo1234", role);
        }
    }

    private void seedFootballData() {
        jdbc.update("insert into player(external_id,full_name,birth_date,nationality,club,league,preferred_foot,primary_position) values (?,?,?,?,?,?,?,?)",
                "ARG-005", "Mateo Ferreyra", LocalDate.of(2001, 4, 18), "Argentina", "Atlético del Sur", "Liga Profesional Argentina", "RIGHT", "DEFENSIVE_MIDFIELDER");
        jdbc.update("insert into player(external_id,full_name,birth_date,nationality,club,league,preferred_foot,primary_position) values (?,?,?,?,?,?,?,?)",
                "ARG-008", "Bruno Acosta", LocalDate.of(2003, 8, 7), "Argentina", "Deportivo Central", "Liga Profesional Argentina", "LEFT", "CENTRAL_MIDFIELDER");
        jdbc.update("insert into player(external_id,full_name,birth_date,nationality,club,league,preferred_foot,primary_position) values (?,?,?,?,?,?,?,?)",
                "URU-021", "Santiago Silva", LocalDate.of(2000, 1, 29), "Uruguay", "Club del Puerto", "Primera División Uruguay", "BOTH", "CENTRAL_MIDFIELDER");
        jdbc.update("insert into player_secondary_position(player_id,position) select id,'CENTRAL_MIDFIELDER' from player where external_id='ARG-005'");
        jdbc.update("insert into player_secondary_position(player_id,position) select id,'ATTACKING_MIDFIELDER' from player where external_id='ARG-008'");

        long scout = users.idByEmail("scout@demo.local");
        for (String external : new String[]{"ARG-005", "ARG-008"}) {
            long player = jdbc.queryForObject("select id from player where external_id=?", Long.class, external);
            jdbc.update("insert into evaluation(player_id,scout_id,observation_date,opponent,competition,observed_position,strengths,weaknesses,general_comment,status,confirmed_at) values (?,?,?,?,?,?,?,?,?,'CONFIRMED',current_timestamp)",
                    player, scout, LocalDate.now().minusDays(external.equals("ARG-005") ? 12 : 8), "Rival de prueba", "Liga local",
                    external.equals("ARG-005") ? "DEFENSIVE_MIDFIELDER" : "CENTRAL_MIDFIELDER",
                    "Lectura de juego y pase seguro", "Puede mejorar el juego aéreo", "Evaluación académica de demostración");
            long evaluation = jdbc.queryForObject("select max(id) from evaluation", Long.class);
            jdbc.update("insert into evaluation_score(evaluation_id,attribute_id,score_value) select ?,id,case code when 'PASSING' then ? when 'RECOVERY' then ? when 'STAMINA' then ? else 7 end from attribute_catalog where category<>'GOALKEEPER'",
                    evaluation, external.equals("ARG-005") ? 9 : 8, external.equals("ARG-005") ? 9 : 7, 8);
        }
    }

    private void seedAdditionalPlayers() {
        List<String> firstNames = List.of(
                "Nicolás", "Franco", "Lautaro", "Matías", "Ignacio",
                "Facundo", "Pedro", "Valentín", "Tomás", "Joaquín");
        List<String> surnames = List.of("Aguirre", "Benítez", "Cabrera", "Duarte");
        List<String> nationalities = List.of("Argentina", "Uruguay", "Chile", "Paraguay");
        List<String> clubs = List.of(
                "Atlético del Sur", "Deportivo Central", "Club del Puerto",
                "Unión del Valle", "Deportivo Andino");
        List<String> feet = List.of("RIGHT", "LEFT", "BOTH");
        Position[] positions = Position.values();

        for (int i = 0; i < 40; i++) {
            String externalId = "DEMO-" + "%03d".formatted(i + 1);
            String fullName = firstNames.get(i % firstNames.size()) + " " + surnames.get(i / firstNames.size());
            LocalDate birthDate = LocalDate.of(1999 + i % 8, 1 + i / 28, 1 + i % 28);
            Long existing = jdbc.queryForObject(
                    "select count(*) from player where external_id=? or (full_name=? and birth_date=?)",
                    Long.class, externalId, fullName, birthDate);
            if (existing == 0) {
                jdbc.update("""
                        insert into player(external_id,full_name,birth_date,nationality,club,league,preferred_foot,primary_position)
                        values (?,?,?,?,?,?,?,?)
                        """, externalId, fullName, birthDate, nationalities.get(i % nationalities.size()),
                        clubs.get(i % clubs.size()), "Liga de Desarrollo", feet.get(i % feet.size()),
                        positions[i % positions.length].name());
            }
        }
    }
}
