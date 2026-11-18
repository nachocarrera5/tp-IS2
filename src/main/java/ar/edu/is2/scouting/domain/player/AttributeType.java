package ar.edu.is2.scouting.domain.player;

public enum AttributeType {
    PASSING,
    CONTROL,
    DRIBBLING,
    FINISHING,
    VISION,
    POSITIONING,
    RECOVERY,
    MARKING,
    SPEED,
    STAMINA,
    STRENGTH,
    AERIAL_PLAY,
    REFLEXES,
    GOALKEEPER_POSITIONING,
    SAVING,
    ONE_ON_ONE,
    DISTRIBUTION,
    FOOTWORK;

    public String label() {
        return switch (this) {
            case PASSING -> "Pase";
            case CONTROL -> "Control";
            case DRIBBLING -> "Conducción";
            case FINISHING -> "Definición";
            case VISION -> "Visión";
            case POSITIONING -> "Posicionamiento";
            case RECOVERY -> "Recuperación";
            case MARKING -> "Marcaje";
            case SPEED -> "Velocidad";
            case STAMINA -> "Resistencia";
            case STRENGTH -> "Fuerza";
            case AERIAL_PLAY -> "Juego aéreo";
            case REFLEXES -> "Reflejos";
            case GOALKEEPER_POSITIONING -> "Colocación";
            case SAVING -> "Atajadas";
            case ONE_ON_ONE -> "Uno contra uno";
            case DISTRIBUTION -> "Distribución";
            case FOOTWORK -> "Juego con los pies";
        };
    }

    public boolean isAvailableFor(Position position) {
        boolean goalkeeperOnly = switch (this) {
            case REFLEXES, GOALKEEPER_POSITIONING, SAVING, ONE_ON_ONE, DISTRIBUTION, FOOTWORK -> true;
            default -> false;
        };
        return goalkeeperOnly
                ? position == Position.GOALKEEPER
                : position != Position.GOALKEEPER || this == AERIAL_PLAY;
    }
}

