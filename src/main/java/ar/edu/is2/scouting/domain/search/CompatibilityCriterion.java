package ar.edu.is2.scouting.domain.search;

import ar.edu.is2.scouting.domain.player.AttributeType;
import java.util.Objects;

public record CompatibilityCriterion(
        AttributeType attribute,
        int targetValue,
        int weight,
        Integer exclusionMinimum) {

    public CompatibilityCriterion {
        Objects.requireNonNull(attribute, "attribute is required");
        requireRange(targetValue, 1, 10, "targetValue");
        requireRange(weight, 1, 5, "weight");
        if (exclusionMinimum != null) {
            requireRange(exclusionMinimum, 1, 10, "exclusionMinimum");
        }
    }

    private static void requireRange(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    field + " must be between " + minimum + " and " + maximum);
        }
    }
}

