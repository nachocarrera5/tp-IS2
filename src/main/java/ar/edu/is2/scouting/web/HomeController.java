package ar.edu.is2.scouting.web;

import java.security.Principal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final JdbcTemplate jdbc;

    public HomeController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/")
    String home(Model model, Principal principal) {
        model.addAttribute("username", principal == null ? null : principal.getName());
        return "index";
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }
    @GetMapping("/app")
    String dashboard(Model model, Principal principal) {
        model.addAttribute("username", principal.getName());
        model.addAttribute("playerCount", count("player"));
        model.addAttribute("evaluationCount", jdbc.queryForObject("select count(*) from evaluation where status='CONFIRMED'", Long.class));
        model.addAttribute("needCount", jdbc.queryForObject("select count(*) from sporting_need where status='ACTIVE'", Long.class));
        model.addAttribute("candidateCount", count("candidate"));
        model.addAttribute("recentNeeds", jdbc.queryForList("select id,title,status,created_at from sporting_need order by created_at desc limit 5"));
        return "app/dashboard";
    }

    @GetMapping("/access-denied")
    String accessDenied() { return "error/access-denied"; }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    // Prototipos visuales aportados por el equipo; los flujos funcionales viven en /players.
    @GetMapping("/register_player")
    String registerPlayer() {
        return "register_player";
    }

    @GetMapping("/score")
    String scorePlayer() {
        return "score_player";
    }
}
