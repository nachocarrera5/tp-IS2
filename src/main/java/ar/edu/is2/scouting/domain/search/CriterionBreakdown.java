package ar.edu.is2.scouting.domain.search;

import ar.edu.is2.scouting.domain.player.AttributeType;
import java.math.BigDecimal;

public record CriterionBreakdown(
        AttributeType attribute,
        BigDecimal playerScore,
        int targetValue,
        int weight,
        BigDecimal contribution) {}

