package ar.edu.is2.scouting.domain.search;

import java.util.List;

public interface CompatibilityStrategy {

    CompatibilityResult calculate(PlayerProfile profile, List<CompatibilityCriterion> criteria);
}

