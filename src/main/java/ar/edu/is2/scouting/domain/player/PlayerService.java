package ar.edu.is2.scouting.domain.player;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {
    private final JdbcTemplate jdbc;

    public PlayerService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record PlayerRow(long id, String externalId, String fullName, LocalDate birthDate,
            String nationality, String club, String league, String preferredFoot,
            Position primaryPosition, List<Position> secondaryPositions, int evaluationCount) {
        public int age() { return java.time.Period.between(birthDate, LocalDate.now()).getYears(); }
    }

    public record PlayerInput(String externalId, String fullName, LocalDate birthDate,
            String nationality, String club, String league, String preferredFoot,
            Position primaryPosition, List<Position> secondaryPositions) {}

    public List<PlayerRow> findAll(String query) {
        String filter = query == null || query.isBlank() ? "%" : "%" + query.toLowerCase() + "%";
        return jdbc.query("""
                select p.*, (select count(*) from evaluation e where e.player_id=p.id) evaluation_count
                from player p where lower(p.full_name) like ? or lower(coalesce(p.club,'')) like ?
                order by p.full_name
                """, (rs, row) -> map(rs.getLong("id"), rs.getString("external_id"), rs.getString("full_name"),
                rs.getObject("birth_date", LocalDate.class), rs.getString("nationality"), rs.getString("club"),
                rs.getString("league"), rs.getString("preferred_foot"), rs.getString("primary_position"),
                rs.getInt("evaluation_count")), filter, filter);
    }

    public PlayerRow find(long id) {
        return jdbc.queryForObject("""
                select p.*, (select count(*) from evaluation e where e.player_id=p.id) evaluation_count
                from player p where p.id=?
                """, (rs, row) -> map(id, rs.getString("external_id"), rs.getString("full_name"),
                rs.getObject("birth_date", LocalDate.class), rs.getString("nationality"), rs.getString("club"),
                rs.getString("league"), rs.getString("preferred_foot"), rs.getString("primary_position"),
                rs.getInt("evaluation_count")), id);
    }

    private PlayerRow map(long id, String external, String name, LocalDate birth, String nationality,
            String club, String league, String foot, String position, int evaluations) {
        var secondary = jdbc.query("select position from player_secondary_position where player_id=? order by position",
                (rs, row) -> Position.valueOf(rs.getString(1)), id);
        return new PlayerRow(id, external, name, birth, nationality, club, league, foot,
                Position.valueOf(position), secondary, evaluations);
    }

    @Transactional
    public long save(Long id, PlayerInput input) {
        validate(input);
        try {
            if (id == null) {
                var key = new GeneratedKeyHolder();
                jdbc.update(connection -> {
                    PreparedStatement ps = connection.prepareStatement("""
                            insert into player(external_id,full_name,birth_date,nationality,club,league,preferred_foot,primary_position)
                            values (?,?,?,?,?,?,?,?)
                            """, Statement.RETURN_GENERATED_KEYS);
                    bind(ps, input);
                    return ps;
                }, key);
                id = ((Number) key.getKeys().get("id")).longValue();
            } else {
                jdbc.update("""
                        update player set external_id=?,full_name=?,birth_date=?,nationality=?,club=?,league=?,
                        preferred_foot=?,primary_position=?,updated_at=current_timestamp where id=?
                        """, blankToNull(input.externalId()), input.fullName().trim(), input.birthDate(),
                        blankToNull(input.nationality()), blankToNull(input.club()), blankToNull(input.league()),
                        blankToNull(input.preferredFoot()), input.primaryPosition().name(), id);
                jdbc.update("delete from player_secondary_position where player_id=?", id);
            }
            for (Position position : input.secondaryPositions() == null ? List.<Position>of() : input.secondaryPositions()) {
                jdbc.update("insert into player_secondary_position(player_id,position) values (?,?)", id, position.name());
            }
            return id;
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Ya existe un jugador con ese identificador o con el mismo nombre y fecha de nacimiento");
        }
    }

    private static void bind(PreparedStatement ps, PlayerInput input) throws java.sql.SQLException {
        ps.setString(1, blankToNull(input.externalId()));
        ps.setString(2, input.fullName().trim());
        ps.setObject(3, input.birthDate());
        ps.setString(4, blankToNull(input.nationality()));
        ps.setString(5, blankToNull(input.club()));
        ps.setString(6, blankToNull(input.league()));
        ps.setString(7, blankToNull(input.preferredFoot()));
        ps.setString(8, input.primaryPosition().name());
    }

    private static void validate(PlayerInput input) {
        if (input.fullName() == null || input.fullName().isBlank()) throw new IllegalArgumentException("El nombre es obligatorio");
        if (input.birthDate() == null || input.birthDate().isAfter(LocalDate.now())) throw new IllegalArgumentException("La fecha de nacimiento no es válida");
        if (input.primaryPosition() == null) throw new IllegalArgumentException("La posición principal es obligatoria");
        if (input.secondaryPositions() != null && input.secondaryPositions().contains(input.primaryPosition()))
            throw new IllegalArgumentException("La posición principal no puede repetirse como secundaria");
    }

    public PlayerInput parseCsvRow(String[] values) {
        if (values.length < 8) throw new IllegalArgumentException("La fila no contiene las ocho columnas obligatorias del formato");
        List<Position> secondary = new ArrayList<>();
        if (values.length > 8 && !values[8].isBlank()) {
            Arrays.stream(values[8].split(";"))
                    .map(String::trim).map(PlayerService::parsePosition).forEach(secondary::add);
        }
        return new PlayerInput(values[0], values[1], LocalDate.parse(values[2]), values[3], values[4],
                values[5], parseFoot(values[6]), parsePosition(values[7]), secondary);
    }

    public Optional<Long> findDuplicate(PlayerInput input) {
        if (input.externalId() != null && !input.externalId().isBlank()) {
            List<Long> byExternalId = jdbc.query(
                    "select id from player where lower(external_id)=lower(?)",
                    (rs, row) -> rs.getLong(1), input.externalId().trim());
            if (!byExternalId.isEmpty()) return Optional.of(byExternalId.getFirst());
        }
        return jdbc.query(
                        "select id from player where lower(full_name)=lower(?) and birth_date=?",
                        (rs, row) -> rs.getLong(1), input.fullName().trim(), input.birthDate())
                .stream().findFirst();
    }

    public static Position parsePosition(String value) {
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toUpperCase().replace(' ', '_');
        return switch (normalized) {
            case "ARQUERO" -> Position.GOALKEEPER;
            case "LATERAL_DERECHO" -> Position.RIGHT_BACK;
            case "LATERAL_IZQUIERDO" -> Position.LEFT_BACK;
            case "DEFENSOR_CENTRAL" -> Position.CENTRE_BACK;
            case "MEDIOCAMPISTA_DEFENSIVO", "VOLANTE_CENTRAL" -> Position.DEFENSIVE_MIDFIELDER;
            case "MEDIOCAMPISTA_CENTRAL", "INTERIOR" -> Position.CENTRAL_MIDFIELDER;
            case "MEDIOCAMPISTA_OFENSIVO", "ENGANCHE" -> Position.ATTACKING_MIDFIELDER;
            case "EXTREMO_DERECHO" -> Position.RIGHT_WINGER;
            case "EXTREMO_IZQUIERDO" -> Position.LEFT_WINGER;
            case "DELANTERO_CENTRO", "DELANTERO" -> Position.CENTRE_FORWARD;
            default -> Position.valueOf(normalized);
        };
    }

    private static String parseFoot(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value.trim().toUpperCase()) {
            case "DERECHO", "RIGHT" -> "RIGHT";
            case "IZQUIERDO", "LEFT" -> "LEFT";
            case "AMBOS", "BOTH" -> "BOTH";
            default -> throw new IllegalArgumentException("Pie dominante inválido: " + value);
        };
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
