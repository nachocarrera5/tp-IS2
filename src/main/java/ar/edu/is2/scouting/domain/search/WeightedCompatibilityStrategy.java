package ar.edu.is2.scouting.domain.search;

import ar.edu.is2.scouting.domain.player.AttributeType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class WeightedCompatibilityStrategy implements CompatibilityStrategy {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 4;

    @Override
    public CompatibilityResult calculate(
            PlayerProfile profile, List<CompatibilityCriterion> criteria) {
        Objects.requireNonNull(profile, "profile is required");
        Objects.requireNonNull(criteria, "criteria are required");
        if (criteria.isEmpty()) {
            throw new IllegalArgumentException("at least one criterion is required");
        }

        if (!profile.hasEvaluations()) {
            return result(CompatibilityStatus.PENDING_EVALUATION, null, Map.of(), List.of());
        }

        var missingAttributes = criteria.stream()
                .map(CompatibilityCriterion::attribute)
                .filter(attribute -> !profile.scores().containsKey(attribute))
                .toList();

        if (!missingAttributes.isEmpty()) {
            return result(
                    CompatibilityStatus.INSUFFICIENT_DATA,
                    null,
                    Map.of(),
                    List.of("Missing attributes: " + missingAttributes));
        }

        var excluded = criteria.stream().anyMatch(criterion -> isBelowMinimum(profile, criterion));
        if (excluded) {
            return result(CompatibilityStatus.EXCLUDED, null, Map.of(), List.of());
        }

        Map<AttributeType, CriterionBreakdown> breakdown = new EnumMap<>(AttributeType.class);
        BigDecimal totalContribution = BigDecimal.ZERO;
        int totalWeight = 0;

        for (var criterion : criteria) {
            var score = profile.scores().get(criterion.attribute());
            var target = BigDecimal.valueOf(criterion.targetValue());
            var fulfillment = score.divide(target, SCALE, RoundingMode.HALF_UP).min(BigDecimal.ONE);
            var contribution = fulfillment.multiply(BigDecimal.valueOf(criterion.weight()));

            breakdown.put(
                    criterion.attribute(),
                    new CriterionBreakdown(
                            criterion.attribute(),
                            score,
                            criterion.targetValue(),
                            criterion.weight(),
                            contribution));
            totalContribution = totalContribution.add(contribution);
            totalWeight += criterion.weight();
        }

        var percentage = totalContribution
                .divide(BigDecimal.valueOf(totalWeight), SCALE, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);

        var warnings = new ArrayList<String>();
        if (profile.scoutCount() == 1) {
            warnings.add("Preliminary result: based on one scout");
        }
        if (profile.usesAlternativePosition()) {
            warnings.add("No evaluations in the requested position; alternative positions were used");
        }

        var status = profile.scoutCount() == 1
                ? CompatibilityStatus.PRELIMINARY
                : CompatibilityStatus.CALCULATED;

        return result(status, percentage, breakdown, warnings);
    }

    private static boolean isBelowMinimum(
            PlayerProfile profile, CompatibilityCriterion criterion) {
        return criterion.exclusionMinimum() != null
                && profile.scores()
                                .get(criterion.attribute())
                                .compareTo(BigDecimal.valueOf(criterion.exclusionMinimum()))
                        < 0;
    }

    private static CompatibilityResult result(
            CompatibilityStatus status,
            BigDecimal percentage,
            Map<AttributeType, CriterionBreakdown> breakdown,
            List<String> warnings) {
        return new CompatibilityResult(status, percentage, breakdown, warnings);
    }
}

