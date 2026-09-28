package ar.edu.is2.scouting.domain.search;

import ar.edu.is2.scouting.domain.player.AttributeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CompatibilityResult(
        CompatibilityStatus status,
        BigDecimal percentage,
        Map<AttributeType, CriterionBreakdown> breakdown,
        List<String> warnings) {

    public CompatibilityResult {
        Objects.requireNonNull(status, "status is required");
        breakdown = Map.copyOf(Objects.requireNonNull(breakdown, "breakdown is required"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings are required"));
    }

    public boolean hasPercentage() {
        return percentage != null;
    }
}

