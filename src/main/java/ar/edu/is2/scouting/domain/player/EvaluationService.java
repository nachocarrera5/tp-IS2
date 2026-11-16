package ar.edu.is2.scouting.domain.player;

import ar.edu.is2.scouting.domain.search.PlayerProfile;
import ar.edu.is2.scouting.domain.user.UserAccountService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationService {
    private final JdbcTemplate jdbc;
    private final UserAccountService users;

    public EvaluationService(JdbcTemplate jdbc, UserAccountService users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    public record AttributeRow(long id, AttributeType type, String name, String category, String description) {}
    public record EvaluationRow(long id, long playerId, String playerName, String scoutName,
            LocalDate observationDate, String opponent, String competition, Position observedPosition,
            String strengths, String weaknesses, String comment, String status, int version,
            Long previousId, OffsetDateTime createdAt, Map<AttributeType, Integer> scores) {}

    public List<AttributeRow> attributes() {
        return jdbc.query("select * from attribute_catalog where active=true order by id",
                (rs, row) -> new AttributeRow(rs.getLong("id"), AttributeType.valueOf(rs.getString("code")),
                        rs.getString("display_name"), rs.getString("category"), rs.getString("description")));
    }

    public List<EvaluationRow> forPlayer(long playerId) {
        return jdbc.query("""
                select e.*,p.full_name player_name,u.full_name scout_name from evaluation e
                join player p on p.id=e.player_id join user_account u on u.id=e.scout_id
                where e.player_id=? order by e.observation_date desc,e.version_number desc
                """, (rs, row) -> map(rs), playerId);
    }

    public List<EvaluationRow> draftsFor(String email) {
        return jdbc.query("""
                select e.*,p.full_name player_name,u.full_name scout_name from evaluation e
                join player p on p.id=e.player_id join user_account u on u.id=e.scout_id
                where e.scout_id=? and e.status='DRAFT' order by e.created_at desc
                """, (rs, row) -> map(rs), users.idByEmail(email));
    }

    public EvaluationRow find(long id) {
        return jdbc.queryForObject("""
                select e.*,p.full_name player_name,u.full_name scout_name from evaluation e
                join player p on p.id=e.player_id join user_account u on u.id=e.scout_id where e.id=?
                """, (rs, row) -> map(rs), id);
    }

    private EvaluationRow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        long id = rs.getLong("id");
        Map<AttributeType, Integer> scores = new EnumMap<>(AttributeType.class);
        jdbc.query("select ac.code,es.score_value from evaluation_score es join attribute_catalog ac on ac.id=es.attribute_id where es.evaluation_id=?",
                (org.springframework.jdbc.core.RowCallbackHandler) result ->
                        scores.put(AttributeType.valueOf(result.getString(1)), result.getInt(2)), id);
        Long previous = rs.getObject("previous_evaluation_id", Long.class);
        return new EvaluationRow(id, rs.getLong("player_id"), rs.getString("player_name"), rs.getString("scout_name"),
                rs.getObject("observation_date", LocalDate.class), rs.getString("opponent"), rs.getString("competition"),
                Position.valueOf(rs.getString("observed_position")), rs.getString("strengths"), rs.getString("weaknesses"),
                rs.getString("general_comment"), rs.getString("status"), rs.getInt("version_number"), previous,
                rs.getObject("created_at", OffsetDateTime.class), scores);
    }

    @Transactional
    public long saveDraft(Long id, long playerId, String email, LocalDate date, String opponent,
            String competition, Position position, String strengths, String weaknesses, String comment,
            Map<AttributeType, Integer> scores, Long correctionOf) {
        validate(date, position, scores);
        long scoutId = users.idByEmail(email);
        int version = 1;
        if (correctionOf != null) {
            EvaluationRow prior = find(correctionOf);
            version = prior.version() + 1;
            playerId = prior.playerId();
        }
        if (id != null) {
            int changed = jdbc.update("""
                    update evaluation set observation_date=?,opponent=?,competition=?,observed_position=?,strengths=?,weaknesses=?,general_comment=?
                    where id=? and scout_id=? and status='DRAFT'
                    """, date, clean(opponent), clean(competition), position.name(), clean(strengths), clean(weaknesses), clean(comment), id, scoutId);
            if (changed == 0) throw new IllegalArgumentException("Solo el autor puede editar un borrador");
            jdbc.update("delete from evaluation_score where evaluation_id=?", id);
        } else {
            var key = new GeneratedKeyHolder();
            long finalPlayerId = playerId;
            int finalVersion = version;
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        insert into evaluation(player_id,scout_id,observation_date,opponent,competition,observed_position,
                        strengths,weaknesses,general_comment,status,version_number,previous_evaluation_id)
                        values (?,?,?,?,?,?,?,?,?,'DRAFT',?,?)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, finalPlayerId); ps.setLong(2, scoutId); ps.setObject(3, date);
                ps.setString(4, clean(opponent)); ps.setString(5, clean(competition)); ps.setString(6, position.name());
                ps.setString(7, clean(strengths)); ps.setString(8, clean(weaknesses)); ps.setString(9, clean(comment));
                ps.setInt(10, finalVersion); if (correctionOf == null) ps.setNull(11, java.sql.Types.BIGINT); else ps.setLong(11, correctionOf);
                return ps;
            }, key);
            id = ((Number) key.getKeys().get("id")).longValue();
        }
        for (var attribute : attributes()) {
            jdbc.update("insert into evaluation_score(evaluation_id,attribute_id,score_value) values (?,?,?)",
                    id, attribute.id(), scores.get(attribute.type()));
        }
        return id;
    }

    @Transactional
    public void confirm(long id, String email) {
        int changed = jdbc.update("update evaluation set status='CONFIRMED',confirmed_at=current_timestamp where id=? and scout_id=? and status='DRAFT'",
                id, users.idByEmail(email));
        if (changed == 0) throw new IllegalArgumentException("La evaluación no puede confirmarse");
    }

    public PlayerProfile profile(long playerId, Position requestedPosition) {
        Integer inPosition = jdbc.queryForObject("""
                select count(*) from evaluation e where e.player_id=? and e.status='CONFIRMED'
                and e.observed_position=? and not exists(select 1 from evaluation newer where newer.previous_evaluation_id=e.id and newer.status='CONFIRMED')
                """, Integer.class, playerId, requestedPosition.name());
        boolean alternative = inPosition == 0;
        String positionClause = alternative ? "" : " and e.observed_position=? ";
        var args = alternative ? new Object[]{playerId} : new Object[]{playerId, requestedPosition.name()};

        Map<Long, Map<AttributeType, List<Integer>>> byScout = new LinkedHashMap<>();
        jdbc.query("""
                select e.scout_id,ac.code,es.score_value from evaluation e
                join evaluation_score es on es.evaluation_id=e.id join attribute_catalog ac on ac.id=es.attribute_id
                where e.player_id=? and e.status='CONFIRMED'
                """ + positionClause + " and not exists(select 1 from evaluation newer where newer.previous_evaluation_id=e.id and newer.status='CONFIRMED')",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> byScout.computeIfAbsent(rs.getLong(1), ignored -> new EnumMap<>(AttributeType.class))
                        .computeIfAbsent(AttributeType.valueOf(rs.getString(2)), ignored -> new ArrayList<>()).add(rs.getInt(3)), args);

        Map<AttributeType, BigDecimal> profile = new EnumMap<>(AttributeType.class);
        for (AttributeType type : AttributeType.values()) {
            List<BigDecimal> scoutAverages = new ArrayList<>();
            for (var scout : byScout.values()) {
                var values = scout.get(type);
                if (values != null && !values.isEmpty()) scoutAverages.add(BigDecimal.valueOf(values.stream().mapToInt(Integer::intValue).average().orElseThrow()));
            }
            if (!scoutAverages.isEmpty()) {
                BigDecimal sum = scoutAverages.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                profile.put(type, sum.divide(BigDecimal.valueOf(scoutAverages.size()), 2, RoundingMode.HALF_UP));
            }
        }
        String countSql = "select count(*),count(distinct scout_id),max(observation_date) from evaluation e where player_id=? and status='CONFIRMED'" + positionClause
                + " and not exists(select 1 from evaluation newer where newer.previous_evaluation_id=e.id and newer.status='CONFIRMED')";
        return jdbc.queryForObject(countSql, (rs, row) -> new PlayerProfile(profile, rs.getInt(2), rs.getInt(1),
                rs.getObject(3, LocalDate.class), alternative && rs.getInt(1) > 0), args);
    }

    private static void validate(LocalDate date, Position position, Map<AttributeType, Integer> scores) {
        if (date == null || date.isAfter(LocalDate.now())) throw new IllegalArgumentException("La fecha de observación no es válida");
        if (position == null) throw new IllegalArgumentException("La posición observada es obligatoria");
        for (AttributeType type : AttributeType.values()) {
            Integer score = scores.get(type);
            if (score == null || score < 1 || score > 10) throw new IllegalArgumentException("Todos los atributos deben tener un valor entre 1 y 10");
        }
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
