package ar.edu.is2.scouting.domain.search;

import ar.edu.is2.scouting.domain.player.AttributeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

public record PlayerProfile(
        Map<AttributeType, BigDecimal> scores,
        int scoutCount,
        int evaluationCount,
        LocalDate lastEvaluationDate,
        boolean usesAlternativePosition) {

    public PlayerProfile {
        scores = Map.copyOf(Objects.requireNonNull(scores, "scores are required"));
        if (scoutCount < 0 || evaluationCount < 0) {
            throw new IllegalArgumentException("counts cannot be negative");
        }
        if (scoutCount > evaluationCount) {
            throw new IllegalArgumentException("scoutCount cannot exceed evaluationCount");
        }
        scores.values().forEach(PlayerProfile::validateScore);
    }

    public boolean hasEvaluations() {
        return evaluationCount > 0;
    }

    private static void validateScore(BigDecimal score) {
        Objects.requireNonNull(score, "score cannot be null");
        if (score.compareTo(BigDecimal.ONE) < 0 || score.compareTo(BigDecimal.TEN) > 0) {
            throw new IllegalArgumentException("scores must be between 1 and 10");
        }
    }
}

