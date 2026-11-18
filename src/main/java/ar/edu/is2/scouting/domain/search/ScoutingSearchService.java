package ar.edu.is2.scouting.domain.search;

import ar.edu.is2.scouting.domain.player.AttributeType;
import ar.edu.is2.scouting.domain.player.EvaluationService;
import ar.edu.is2.scouting.domain.player.PlayerService;
import ar.edu.is2.scouting.domain.player.Position;
import ar.edu.is2.scouting.domain.user.UserAccountService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScoutingSearchService {
    private final JdbcTemplate jdbc;
    private final PlayerService players;
    private final EvaluationService evaluations;
    private final CompatibilityStrategy strategy;
    private final UserAccountService users;

    public ScoutingSearchService(JdbcTemplate jdbc, PlayerService players, EvaluationService evaluations,
            CompatibilityStrategy strategy, UserAccountService users) {
        this.jdbc = jdbc; this.players = players; this.evaluations = evaluations; this.strategy = strategy; this.users = users;
    }

    public record CriterionRow(long id, AttributeType attribute, String attributeName, int target, int weight, Integer minimum) {}
    public record NeedRow(long id, String title, String description, String status, OffsetDateTime createdAt,
            Long versionId, int version, Position position, String league, Integer minimumAge, Integer maximumAge,
            String nationality, String preferredFoot, List<CriterionRow> criteria, int executionCount, int candidateCount) {}
    public record ResultRow(long id, long executionId, long playerId, String playerName, String club,
            String primaryPosition, java.math.BigDecimal percentage, CompatibilityStatus status, String positionMatch,
            int scoutCount, int evaluationCount, LocalDate lastEvaluation, boolean alternativePosition,
            List<CriterionBreakdown> breakdown, boolean candidate) {}
    public record ExecutionRow(long id, long needVersionId, String needTitle, int version, OffsetDateTime executedAt,
            int analyzedCount, int resultCount, List<ResultRow> results) {}
    public record NeedInput(String title, String description, Position position, String league, Integer minimumAge,
            Integer maximumAge, String nationality, String preferredFoot, List<CriterionInput> criteria) {}
    public record CriterionInput(AttributeType attribute, int target, int weight, Integer minimum) {}

    public List<NeedRow> findAll() {
        return jdbc.query("""
                select n.*,v.id version_id,v.version_number,v.requested_position,v.league,v.minimum_age,v.maximum_age,
                v.nationality,v.preferred_foot,
                (select count(*) from search_execution x join need_version nv on nv.id=x.need_version_id where nv.need_id=n.id) executions,
                (select count(*) from candidate c join need_version nv on nv.id=c.need_version_id where nv.need_id=n.id) candidates
                from sporting_need n left join need_version v on v.id=(select max(v2.id) from need_version v2 where v2.need_id=n.id)
                order by n.created_at desc
                """, (rs, row) -> mapNeed(rs));
    }

    public NeedRow findNeed(long id) {
        return jdbc.queryForObject("""
                select n.*,v.id version_id,v.version_number,v.requested_position,v.league,v.minimum_age,v.maximum_age,
                v.nationality,v.preferred_foot,
                (select count(*) from search_execution x join need_version nv on nv.id=x.need_version_id where nv.need_id=n.id) executions,
                (select count(*) from candidate c join need_version nv on nv.id=c.need_version_id where nv.need_id=n.id) candidates
                from sporting_need n join need_version v on v.id=(select max(v2.id) from need_version v2 where v2.need_id=n.id)
                where n.id=?
                """, (rs, row) -> mapNeed(rs), id);
    }

    private NeedRow mapNeed(java.sql.ResultSet rs) throws java.sql.SQLException {
        long versionId = rs.getLong("version_id");
        var criteria = criteria(versionId);
        return new NeedRow(rs.getLong("id"), rs.getString("title"), rs.getString("description"), rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class), versionId, rs.getInt("version_number"),
                Position.valueOf(rs.getString("requested_position")), rs.getString("league"),
                rs.getObject("minimum_age", Integer.class), rs.getObject("maximum_age", Integer.class),
                rs.getString("nationality"), rs.getString("preferred_foot"), criteria,
                rs.getInt("executions"), rs.getInt("candidates"));
    }

    public List<CriterionRow> criteria(long versionId) {
        return jdbc.query("""
                select c.*,a.code,a.display_name from search_criterion c join attribute_catalog a on a.id=c.attribute_id
                where c.need_version_id=? order by c.weight desc,a.id
                """, (rs, row) -> new CriterionRow(rs.getLong("id"), AttributeType.valueOf(rs.getString("code")),
                rs.getString("display_name"), rs.getInt("target_value"), rs.getInt("weight"),
                rs.getObject("exclusion_minimum", Integer.class)), versionId);
    }

    @Transactional
    public long create(NeedInput input, String email) {
        validate(input);
        long creator = users.idByEmail(email);
        long needId = insert("insert into sporting_need(title,description,created_by) values (?,?,?)",
                ps -> { ps.setString(1, input.title().trim()); ps.setString(2, clean(input.description())); ps.setLong(3, creator); });
        createVersion(needId, 1, input, creator);
        return needId;
    }

    @Transactional
    public void addVersion(long needId, NeedInput input, String email) {
        validate(input);
        String status = jdbc.queryForObject("select status from sporting_need where id=?", String.class, needId);
        if (!"ACTIVE".equals(status)) throw new IllegalArgumentException("No se puede versionar una necesidad cerrada");
        Integer next = jdbc.queryForObject("select coalesce(max(version_number),0)+1 from need_version where need_id=?", Integer.class, needId);
        createVersion(needId, next, input, users.idByEmail(email));
    }

    private void createVersion(long needId, int version, NeedInput input, long creator) {
        long versionId = insert("""
                insert into need_version(need_id,version_number,requested_position,league,minimum_age,maximum_age,nationality,preferred_foot,created_by)
                values (?,?,?,?,?,?,?,?,?)
                """, ps -> { ps.setLong(1, needId); ps.setInt(2, version); ps.setString(3, input.position().name());
                    ps.setString(4, clean(input.league())); setInteger(ps, 5, input.minimumAge()); setInteger(ps, 6, input.maximumAge());
                    ps.setString(7, clean(input.nationality())); ps.setString(8, clean(input.preferredFoot())); ps.setLong(9, creator); });
        for (CriterionInput criterion : input.criteria()) {
            long attributeId = jdbc.queryForObject("select id from attribute_catalog where code=?", Long.class, criterion.attribute().name());
            jdbc.update("insert into search_criterion(need_version_id,attribute_id,target_value,weight,exclusion_minimum) values (?,?,?,?,?)",
                    versionId, attributeId, criterion.target(), criterion.weight(), criterion.minimum());
        }
    }

    @Transactional
    public long execute(long needId, String email) {
        NeedRow need = findNeed(needId);
        if (!"ACTIVE".equals(need.status())) throw new IllegalArgumentException("La necesidad está cerrada");
        List<PlayerService.PlayerRow> eligible = players.findAll(null).stream().filter(player -> matches(player, need)).toList();
        long executionId = insert("insert into search_execution(need_version_id,executed_by,analyzed_player_count) values (?,?,?)",
                ps -> { ps.setLong(1, need.versionId()); ps.setLong(2, users.idByEmail(email)); ps.setInt(3, eligible.size()); });
        int saved = 0;
        List<CompatibilityCriterion> criteria = need.criteria().stream()
                .map(c -> new CompatibilityCriterion(c.attribute(), c.target(), c.weight(), c.minimum())).toList();
        for (PlayerService.PlayerRow player : eligible) {
            PlayerProfile profile = evaluations.profile(player.id(), need.position());
            CompatibilityResult result = strategy.calculate(profile, criteria);
            if (result.status() == CompatibilityStatus.EXCLUDED) continue;
            String match = player.primaryPosition() == need.position() ? "PRIMARY" : "SECONDARY";
            insert("""
                    insert into search_result(execution_id,player_id,compatibility_percentage,calculation_status,position_match_type,
                    scout_count,evaluation_count,last_evaluation_date,uses_alternative_position,calculation_detail)
                    values (?,?,?,?,?,?,?,?,?,?)
                    """, ps -> { ps.setLong(1, executionId); ps.setLong(2, player.id()); ps.setBigDecimal(3, result.percentage());
                        ps.setString(4, result.status().name()); ps.setString(5, match); ps.setInt(6, profile.scoutCount());
                        ps.setInt(7, profile.evaluationCount()); ps.setObject(8, profile.lastEvaluationDate());
                        ps.setBoolean(9, profile.usesAlternativePosition()); ps.setString(10, serialize(result)); });
            saved++;
        }
        jdbc.update("update search_execution set result_count=? where id=?", saved, executionId);
        return executionId;
    }

    private boolean matches(PlayerService.PlayerRow player, NeedRow need) {
        boolean position = player.primaryPosition() == need.position() || player.secondaryPositions().contains(need.position());
        if (!position) return false;
        if (need.league() != null && !need.league().equalsIgnoreCase(player.league())) return false;
        if (need.nationality() != null && !need.nationality().equalsIgnoreCase(player.nationality())) return false;
        if (need.preferredFoot() != null && !need.preferredFoot().equalsIgnoreCase(player.preferredFoot())) return false;
        return (need.minimumAge() == null || player.age() >= need.minimumAge())
                && (need.maximumAge() == null || player.age() <= need.maximumAge());
    }

    public ExecutionRow findExecution(long id) {
        return jdbc.queryForObject("""
                select x.*,n.title,v.version_number from search_execution x join need_version v on v.id=x.need_version_id
                join sporting_need n on n.id=v.need_id where x.id=?
                """, (rs, row) -> new ExecutionRow(id, rs.getLong("need_version_id"), rs.getString("title"),
                rs.getInt("version_number"), rs.getObject("executed_at", OffsetDateTime.class),
                rs.getInt("analyzed_player_count"), rs.getInt("result_count"), results(id)), id);
    }

    public List<ExecutionRow> executions(long needId) {
        return jdbc.query("""
                select x.*,n.title,v.version_number from search_execution x join need_version v on v.id=x.need_version_id
                join sporting_need n on n.id=v.need_id where n.id=? order by x.executed_at desc
                """, (rs, row) -> new ExecutionRow(rs.getLong("id"), rs.getLong("need_version_id"), rs.getString("title"),
                rs.getInt("version_number"), rs.getObject("executed_at", OffsetDateTime.class), rs.getInt("analyzed_player_count"),
                rs.getInt("result_count"), List.of()), needId);
    }

    private List<ResultRow> results(long executionId) {
        return jdbc.query("""
                select r.*,p.full_name,p.club,p.primary_position,
                exists(select 1 from candidate c where c.source_result_id=r.id) candidate
                from search_result r join player p on p.id=r.player_id where r.execution_id=?
                order by case when r.compatibility_percentage is null then 1 else 0 end,r.compatibility_percentage desc,p.full_name
                """, (rs, row) -> new ResultRow(rs.getLong("id"), executionId, rs.getLong("player_id"), rs.getString("full_name"),
                rs.getString("club"), rs.getString("primary_position"), rs.getBigDecimal("compatibility_percentage"),
                CompatibilityStatus.valueOf(rs.getString("calculation_status")), rs.getString("position_match_type"),
                rs.getInt("scout_count"), rs.getInt("evaluation_count"), rs.getObject("last_evaluation_date", LocalDate.class),
                rs.getBoolean("uses_alternative_position"), deserialize(rs.getString("calculation_detail")), rs.getBoolean("candidate")), executionId);
    }

    public void close(long needId) {
        jdbc.update("update sporting_need set status='CLOSED',closed_at=current_timestamp where id=? and status='ACTIVE'", needId);
    }

    private static String serialize(CompatibilityResult result) {
        return result.breakdown().values().stream().map(b -> String.join("|", b.attribute().name(), b.playerScore().toString(),
                Integer.toString(b.targetValue()), Integer.toString(b.weight()), b.contribution().toString())).collect(java.util.stream.Collectors.joining(";"));
    }

    private static List<CriterionBreakdown> deserialize(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<CriterionBreakdown> result = new ArrayList<>();
        for (String item : value.split(";")) {
            String[] p = item.split("\\|");
            result.add(new CriterionBreakdown(AttributeType.valueOf(p[0]), new java.math.BigDecimal(p[1]),
                    Integer.parseInt(p[2]), Integer.parseInt(p[3]), new java.math.BigDecimal(p[4])));
        }
        return result;
    }

    private static void validate(NeedInput input) {
        if (input.title() == null || input.title().isBlank()) throw new IllegalArgumentException("El título es obligatorio");
        if (input.position() == null) throw new IllegalArgumentException("La posición es obligatoria");
        if (input.minimumAge() != null && input.maximumAge() != null && input.minimumAge() > input.maximumAge())
            throw new IllegalArgumentException("El rango de edad no es válido");
        if (input.criteria() == null || input.criteria().isEmpty()) throw new IllegalArgumentException("Elegí al menos un criterio deportivo");
        long distinct = input.criteria().stream().map(CriterionInput::attribute).distinct().count();
        if (distinct != input.criteria().size()) throw new IllegalArgumentException("No se puede repetir un atributo");
        input.criteria().forEach(c -> {
            if (!c.attribute().isAvailableFor(input.position())) {
                throw new IllegalArgumentException("El atributo " + c.attribute().label() + " no corresponde a la posición elegida");
            }
            new CompatibilityCriterion(c.attribute(), c.target(), c.weight(), c.minimum());
        });
    }

    private long insert(String sql, SqlBinder binder) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> { PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS); binder.bind(ps); return ps; }, key);
        return ((Number) key.getKeys().get("id")).longValue();
    }
    @FunctionalInterface private interface SqlBinder { void bind(PreparedStatement ps) throws java.sql.SQLException; }
    private static void setInteger(PreparedStatement ps, int index, Integer value) throws java.sql.SQLException {
        if (value == null) ps.setNull(index, java.sql.Types.INTEGER); else ps.setInt(index, value);
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
