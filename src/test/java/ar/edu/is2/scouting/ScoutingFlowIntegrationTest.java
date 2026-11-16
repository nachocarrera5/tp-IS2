package ar.edu.is2.scouting;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.is2.scouting.domain.player.AttributeType;
import ar.edu.is2.scouting.domain.player.EvaluationService;
import ar.edu.is2.scouting.domain.player.PlayerService;
import ar.edu.is2.scouting.domain.player.Position;
import ar.edu.is2.scouting.domain.search.CandidateService;
import ar.edu.is2.scouting.domain.search.ScoutingSearchService;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScoutingFlowIntegrationTest {
    @Autowired private ScoutingSearchService searches;
    @Autowired private CandidateService candidates;
    @Autowired private EvaluationService evaluations;
    @Autowired private PlayerService players;

    @Test
    void createsNeedExecutesExplainableRankingAndTracksCandidate() {
        long needId = searches.create(new ScoutingSearchService.NeedInput(
                "Volante central para integración", "Buen pase y recuperación",
                Position.CENTRAL_MIDFIELDER, "Liga Profesional Argentina", 20, 30,
                null, null, List.of(
                        new ScoutingSearchService.CriterionInput(AttributeType.PASSING, 8, 5, 6),
                        new ScoutingSearchService.CriterionInput(AttributeType.RECOVERY, 7, 3, null),
                        new ScoutingSearchService.CriterionInput(AttributeType.STAMINA, 8, 2, 6))),
                "coordinador@demo.local");

        long executionId = searches.execute(needId, "coordinador@demo.local");
        var execution = searches.findExecution(executionId);

        assertThat(execution.analyzedCount()).isEqualTo(2);
        assertThat(execution.results()).hasSize(2);
        assertThat(execution.results()).allSatisfy(result -> {
            assertThat(result.percentage()).isNotNull();
            assertThat(result.breakdown()).hasSize(3);
        });

        long candidateId = candidates.select(execution.results().getFirst().id(), "coordinador@demo.local");
        candidates.changeStatus(candidateId, CandidateService.CandidateStatus.UNDER_OBSERVATION,
                "Seguimiento técnico", "coordinador@demo.local");

        assertThat(candidates.find(candidateId).status())
                .isEqualTo(CandidateService.CandidateStatus.UNDER_OBSERVATION);
        assertThat(candidates.history(candidateId)).hasSize(2);
    }

    @Test
    void confirmedCorrectionReplacesPreviousEvaluationInPlayerProfile() {
        long playerId = players.findAll("Santiago Silva").getFirst().id();
        EnumMap<AttributeType, Integer> firstScores = scores(6);
        long first = evaluations.saveDraft(null, playerId, "scout@demo.local", LocalDate.now(),
                "Rival A", "Liga", Position.CENTRAL_MIDFIELDER,
                "Pase", "Marca", "Primer informe", firstScores, null);
        evaluations.confirm(first, "scout@demo.local");

        EnumMap<AttributeType, Integer> correctedScores = scores(9);
        long correction = evaluations.saveDraft(null, playerId, "scout@demo.local", LocalDate.now(),
                "Rival A", "Liga", Position.CENTRAL_MIDFIELDER,
                "Pase mejor observado", "Marca", "Corrección", correctedScores, first);
        evaluations.confirm(correction, "scout@demo.local");

        var profile = evaluations.profile(playerId, Position.CENTRAL_MIDFIELDER);
        assertThat(profile.evaluationCount()).isEqualTo(1);
        assertThat(profile.scoutCount()).isEqualTo(1);
        assertThat(profile.scores().get(AttributeType.PASSING)).isEqualByComparingTo("9.00");
    }

    @Test
    void detectsCsvDuplicatesByExternalIdOrIdentity() {
        var existing = players.findAll("Bruno Acosta").getFirst();
        var byExternal = new PlayerService.PlayerInput(existing.externalId(), "Otro nombre",
                LocalDate.of(2002, 1, 1), "Argentina", "Club", "Liga", "RIGHT",
                Position.CENTRAL_MIDFIELDER, List.of());
        var byIdentity = new PlayerService.PlayerInput(null, existing.fullName(), existing.birthDate(),
                "Argentina", "Club", "Liga", "LEFT", Position.CENTRAL_MIDFIELDER, List.of());

        assertThat(players.findDuplicate(byExternal)).contains(existing.id());
        assertThat(players.findDuplicate(byIdentity)).contains(existing.id());
    }

    private static EnumMap<AttributeType, Integer> scores(int value) {
        var scores = new EnumMap<AttributeType, Integer>(AttributeType.class);
        for (AttributeType attribute : AttributeType.values()) scores.put(attribute, value);
        return scores;
    }
}
