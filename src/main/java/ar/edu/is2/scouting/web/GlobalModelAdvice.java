package ar.edu.is2.scouting.web;

import ar.edu.is2.scouting.domain.player.Position;
import java.security.Principal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAdvice {
    @ModelAttribute("positions")
    Position[] positions() { return Position.values(); }

    @ModelAttribute("currentUser")
    String currentUser(Principal principal) { return principal == null ? null : principal.getName(); }

    @ModelAttribute("isCoordinator")
    boolean isCoordinator(Authentication auth) { return has(auth, "ROLE_COORDINATOR"); }

    @ModelAttribute("isScout")
    boolean isScout(Authentication auth) { return has(auth, "ROLE_SCOUT"); }

    @ModelAttribute("isDirector")
    boolean isDirector(Authentication auth) { return has(auth, "ROLE_DIRECTOR"); }

    private boolean has(Authentication auth, String role) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }
}
