package ar.edu.is2.scouting.domain.player;

public enum Position {
    GOALKEEPER,
    RIGHT_BACK,
    LEFT_BACK,
    CENTRE_BACK,
    DEFENSIVE_MIDFIELDER,
    CENTRAL_MIDFIELDER,
    ATTACKING_MIDFIELDER,
    RIGHT_WINGER,
    LEFT_WINGER,
    CENTRE_FORWARD;

    public String label() {
        return switch (this) {
            case GOALKEEPER -> "Arquero";
            case RIGHT_BACK -> "Lateral derecho";
            case LEFT_BACK -> "Lateral izquierdo";
            case CENTRE_BACK -> "Defensor central";
            case DEFENSIVE_MIDFIELDER -> "Mediocampista defensivo";
            case CENTRAL_MIDFIELDER -> "Mediocampista central";
            case ATTACKING_MIDFIELDER -> "Mediocampista ofensivo";
            case RIGHT_WINGER -> "Extremo derecho";
            case LEFT_WINGER -> "Extremo izquierdo";
            case CENTRE_FORWARD -> "Delantero centro";
        };
    }
}

