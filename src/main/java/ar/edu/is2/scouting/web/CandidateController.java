package ar.edu.is2.scouting.web;

import ar.edu.is2.scouting.domain.search.CandidateService;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CandidateController {
    private final CandidateService candidates;
    public CandidateController(CandidateService candidates) { this.candidates = candidates; }

    @GetMapping("/candidates")
    String list(Model model) { model.addAttribute("candidates", candidates.findAll()); return "candidates/list"; }

    @GetMapping("/candidates/{id}")
    String detail(@PathVariable long id, Model model) {
        model.addAttribute("candidate", candidates.find(id)); model.addAttribute("history", candidates.history(id));
        model.addAttribute("statuses", CandidateService.CandidateStatus.values()); return "candidates/detail";
    }

    @PostMapping("/players/{playerId}/follow")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'DIRECTOR')")
    String followPlayer(@PathVariable long playerId, Principal principal, RedirectAttributes redirect) {
        try {
            long candidate = candidates.followManually(playerId, principal.getName());
            redirect.addFlashAttribute("success", "Jugador incorporado al seguimiento");
            return "redirect:/candidates/" + candidate;
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
            return "redirect:/players";
        }
    }

    @PostMapping("/candidates/{id}/status")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'DIRECTOR')")
    String status(@PathVariable long id, @RequestParam CandidateService.CandidateStatus status,
            @RequestParam(required = false) String reason, Principal principal, RedirectAttributes redirect) {
        try { candidates.changeStatus(id, status, reason, principal.getName()); redirect.addFlashAttribute("success", "Estado actualizado"); }
        catch (IllegalArgumentException exception) { redirect.addFlashAttribute("error", exception.getMessage()); }
        return "redirect:/candidates/" + id;
    }
}
