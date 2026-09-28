package ar.edu.is2.scouting.domain.user;

public enum UserRole {
    DIRECTOR,
    COORDINATOR,
    SCOUT;

    public String securityName() {
        return name();
    }
}

