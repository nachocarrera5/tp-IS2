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
    AERIAL_PLAY;

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
        };
    }
}

