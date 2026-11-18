package ar.edu.is2.scouting.domain.search;

import ar.edu.is2.scouting.domain.user.UserAccountService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateService {
    private final JdbcTemplate jdbc;
    private final UserAccountService users;
    public CandidateService(JdbcTemplate jdbc, UserAccountService users) { this.jdbc = jdbc; this.users = users; }

    public enum CandidateStatus { DETECTED, UNDER_OBSERVATION, SHORTLISTED, RECOMMENDED, DISCARDED }
    public record CandidateRow(long id, long playerId, String playerName, String club, String needTitle, Integer needVersion,
            CandidateStatus status, OffsetDateTime selectedAt, java.math.BigDecimal sourcePercentage) {}
    public record StatusChangeRow(String previousStatus, String newStatus, String reason, String changedBy, OffsetDateTime changedAt) {}
    private record ManualFollowRow(long id, CandidateStatus status) {}

    public List<CandidateRow> findAll() {
        return jdbc.query("""
                select c.*,p.full_name,p.club,n.title,v.version_number,r.compatibility_percentage from candidate c
                join player p on p.id=c.player_id left join need_version v on v.id=c.need_version_id
                left join sporting_need n on n.id=v.need_id left join search_result r on r.id=c.source_result_id
                order by c.selected_at desc
                """, (rs, row) -> map(rs));
    }

    public CandidateRow find(long id) {
        return jdbc.queryForObject("""
                select c.*,p.full_name,p.club,n.title,v.version_number,r.compatibility_percentage from candidate c
                join player p on p.id=c.player_id left join need_version v on v.id=c.need_version_id
                left join sporting_need n on n.id=v.need_id left join search_result r on r.id=c.source_result_id where c.id=?
                """, (rs, row) -> map(rs), id);
    }

    private CandidateRow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CandidateRow(rs.getLong("id"), rs.getLong("player_id"), rs.getString("full_name"), rs.getString("club"),
                rs.getString("title"), rs.getObject("version_number", Integer.class), CandidateStatus.valueOf(rs.getString("current_status")),
                rs.getObject("selected_at", OffsetDateTime.class), rs.getBigDecimal("compatibility_percentage"));
    }

    public Set<Long> followedPlayerIds() {
        return new HashSet<>(jdbc.queryForList(
                "select distinct player_id from candidate where current_status<>'DISCARDED'", Long.class));
    }

    public List<StatusChangeRow> history(long candidateId) {
        return jdbc.query("""
                select h.*,u.full_name from candidate_status_change h join user_account u on u.id=h.changed_by
                where h.candidate_id=? order by h.changed_at desc
                """, (rs, row) -> new StatusChangeRow(rs.getString("previous_status"), rs.getString("new_status"),
                rs.getString("reason"), rs.getString("full_name"), rs.getObject("changed_at", OffsetDateTime.class)), candidateId);
    }

    @Transactional
    public long select(long resultId, String email) {
        var data = jdbc.queryForMap("select player_id,execution_id from search_result where id=?", resultId);
        long version = jdbc.queryForObject("select need_version_id from search_execution where id=?", Long.class, data.get("execution_id"));
        long user = users.idByEmail(email);
        var key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("insert into candidate(player_id,need_version_id,source_result_id,selected_by) values (?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, ((Number) data.get("player_id")).longValue()); ps.setLong(2, version); ps.setLong(3, resultId); ps.setLong(4, user); return ps;
            }, key);
        } catch (DuplicateKeyException exception) { throw new IllegalArgumentException("El jugador ya está en seguimiento para esta versión"); }
        long id = ((Number) key.getKeys().get("id")).longValue();
        jdbc.update("insert into candidate_status_change(candidate_id,previous_status,new_status,reason,changed_by) values (?,null,'DETECTED','Seleccionado desde el ranking',?)", id, user);
        return id;
    }

    @Transactional
    public long followManually(long playerId, String email) {
        long user = users.idByEmail(email);
        List<Long> activeFollows = jdbc.queryForList(
                "select id from candidate where player_id=? and current_status<>'DISCARDED'", Long.class, playerId);
        if (!activeFollows.isEmpty()) {
            throw new IllegalArgumentException("El jugador ya está en seguimiento");
        }
        var existing = jdbc.query("""
                select id,current_status from candidate where manual_follow_player_id=?
                """, (rs, row) -> new ManualFollowRow(rs.getLong("id"),
                        CandidateStatus.valueOf(rs.getString("current_status"))),
                playerId);
        if (!existing.isEmpty()) {
            ManualFollowRow follow = existing.getFirst();
            if (follow.status() == CandidateStatus.DISCARDED) {
                changeStatus(follow.id(), CandidateStatus.UNDER_OBSERVATION,
                        "Reincorporado desde Jugadores", email);
                return follow.id();
            }
            throw new IllegalArgumentException("El jugador ya está en seguimiento");
        }
        var key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "insert into candidate(player_id,manual_follow_player_id,selected_by) values (?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, playerId);
                ps.setLong(2, playerId);
                ps.setLong(3, user);
                return ps;
            }, key);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("El jugador ya está en seguimiento manual");
        }
        long id = ((Number) key.getKeys().get("id")).longValue();
        jdbc.update("insert into candidate_status_change(candidate_id,previous_status,new_status,reason,changed_by) values (?,null,'DETECTED','Seguimiento manual desde Jugadores',?)",
                id, user);
        return id;
    }

    @Transactional
    public void changeStatus(long id, CandidateStatus next, String reason, String email) {
        CandidateRow current = find(id);
        if (next == CandidateStatus.DISCARDED && (reason == null || reason.isBlank())) throw new IllegalArgumentException("Descartar exige indicar un motivo");
        if (!allowed(current.status(), next)) throw new IllegalArgumentException("La transición de estado no está permitida");
        long user = users.idByEmail(email);
        jdbc.update("update candidate set current_status=? where id=?", next.name(), id);
        jdbc.update("insert into candidate_status_change(candidate_id,previous_status,new_status,reason,changed_by) values (?,?,?,?,?)",
                id, current.status().name(), next.name(), reason == null || reason.isBlank() ? null : reason.trim(), user);
    }

    private static boolean allowed(CandidateStatus from, CandidateStatus to) {
        return switch (from) {
            case DETECTED -> to == CandidateStatus.UNDER_OBSERVATION || to == CandidateStatus.DISCARDED;
            case UNDER_OBSERVATION -> to == CandidateStatus.SHORTLISTED || to == CandidateStatus.DISCARDED;
            case SHORTLISTED -> to == CandidateStatus.RECOMMENDED || to == CandidateStatus.UNDER_OBSERVATION || to == CandidateStatus.DISCARDED;
            case RECOMMENDED, DISCARDED -> to == CandidateStatus.UNDER_OBSERVATION;
        };
    }
}
