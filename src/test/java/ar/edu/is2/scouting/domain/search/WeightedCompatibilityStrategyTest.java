package ar.edu.is2.scouting.domain.search;

import static ar.edu.is2.scouting.domain.player.AttributeType.PASSING;
import static ar.edu.is2.scouting.domain.player.AttributeType.RECOVERY;
import static ar.edu.is2.scouting.domain.player.AttributeType.STAMINA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeightedCompatibilityStrategyTest {

    private final WeightedCompatibilityStrategy strategy = new WeightedCompatibilityStrategy();

    @Test
    void calculatesTheExampleDefinedInTheSpecification() {
        var profile = profile(
                Map.of(
                        PASSING, BigDecimal.valueOf(7),
                        RECOVERY, BigDecimal.valueOf(8),
                        STAMINA, BigDecimal.valueOf(8)),
                3,
                7,
                false);
        var criteria = List.of(
                new CompatibilityCriterion(PASSING, 8, 5, 6),
                new CompatibilityCriterion(RECOVERY, 7, 3, null),
                new CompatibilityCriterion(STAMINA, 8, 2, 6));

        var result = strategy.calculate(profile, criteria);

        assertThat(result.status()).isEqualTo(CompatibilityStatus.CALCULATED);
        assertThat(result.percentage()).isEqualByComparingTo("93.75");
        assertThat(result.breakdown()).containsKeys(PASSING, RECOVERY, STAMINA);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void marksAResultAsPreliminaryWhenOnlyOneScoutContributed() {
        var profile = profile(Map.of(PASSING, BigDecimal.valueOf(8)), 1, 2, false);

        var result = strategy.calculate(
                profile, List.of(new CompatibilityCriterion(PASSING, 8, 5, null)));

        assertThat(result.status()).isEqualTo(CompatibilityStatus.PRELIMINARY);
        assertThat(result.warnings()).contains("Preliminary result: based on one scout");
    }

    @Test
    void excludesAPlayerBelowAnExplicitMinimum() {
        var profile = profile(Map.of(PASSING, BigDecimal.valueOf(5)), 2, 2, false);

        var result = strategy.calculate(
                profile, List.of(new CompatibilityCriterion(PASSING, 8, 5, 6)));

        assertThat(result.status()).isEqualTo(CompatibilityStatus.EXCLUDED);
        assertThat(result.hasPercentage()).isFalse();
    }

    @Test
    void reportsInsufficientDataWhenARequiredAttributeIsMissing() {
        var profile = profile(Map.of(PASSING, BigDecimal.valueOf(8)), 2, 3, false);

        var result = strategy.calculate(
                profile,
                List.of(
                        new CompatibilityCriterion(PASSING, 8, 5, null),
                        new CompatibilityCriterion(RECOVERY, 7, 3, null)));

        assertThat(result.status()).isEqualTo(CompatibilityStatus.INSUFFICIENT_DATA);
        assertThat(result.hasPercentage()).isFalse();
        assertThat(result.warnings()).singleElement().asString().contains("RECOVERY");
    }

    @Test
    void reportsPendingEvaluationForAnEmptyProfile() {
        var profile = profile(Map.of(), 0, 0, false);

        var result = strategy.calculate(
                profile, List.of(new CompatibilityCriterion(PASSING, 8, 5, null)));

        assertThat(result.status()).isEqualTo(CompatibilityStatus.PENDING_EVALUATION);
        assertThat(result.hasPercentage()).isFalse();
    }

    @Test
    void warnsWhenAlternativePositionEvaluationsWereUsed() {
        var profile = profile(Map.of(PASSING, BigDecimal.valueOf(8)), 2, 4, true);

        var result = strategy.calculate(
                profile, List.of(new CompatibilityCriterion(PASSING, 8, 5, null)));

        assertThat(result.warnings())
                .contains("No evaluations in the requested position; alternative positions were used");
    }

    @Test
    void rejectsAnEmptyCriterionList() {
        var profile = profile(Map.of(PASSING, BigDecimal.valueOf(8)), 2, 3, false);

        assertThatThrownBy(() -> strategy.calculate(profile, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one criterion");
    }

    private static PlayerProfile profile(
            Map<ar.edu.is2.scouting.domain.player.AttributeType, BigDecimal> scores,
            int scouts,
            int evaluations,
            boolean alternativePosition) {
        return new PlayerProfile(
                scores, scouts, evaluations, LocalDate.of(2026, 9, 28), alternativePosition);
    }
}

