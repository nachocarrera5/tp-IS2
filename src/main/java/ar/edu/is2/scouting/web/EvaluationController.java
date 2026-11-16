package ar.edu.is2.scouting.web;

import ar.edu.is2.scouting.domain.player.AttributeType;
import ar.edu.is2.scouting.domain.player.EvaluationService;
import ar.edu.is2.scouting.domain.player.PlayerService;
import ar.edu.is2.scouting.domain.player.Position;
import java.security.Principal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class EvaluationController {
    private final EvaluationService evaluations;
    private final PlayerService players;
    public EvaluationController(EvaluationService evaluations, PlayerService players) { this.evaluations = evaluations; this.players = players; }

    @GetMapping("/evaluations")
    String drafts(Principal principal, Model model) {
        model.addAttribute("drafts", evaluations.draftsFor(principal.getName())); return "evaluations/list";
    }

    @GetMapping("/evaluations/new")
    String newEvaluation(@RequestParam long playerId, Model model) {
        prepare(model, playerId); return "evaluations/form";
    }

    @GetMapping("/evaluations/{id}/edit")
    String edit(@PathVariable long id, Model model) {
        var evaluation = evaluations.find(id); model.addAttribute("evaluation", evaluation); prepare(model, evaluation.playerId()); return "evaluations/form";
    }

    @GetMapping("/evaluations/{id}/correct")
    String correct(@PathVariable long id, Model model) {
        var evaluation = evaluations.find(id); model.addAttribute("evaluation", evaluation);
        model.addAttribute("correctionOf", id); prepare(model, evaluation.playerId()); return "evaluations/form";
    }

    @PostMapping("/evaluations")
    String save(@RequestParam(required = false) Long id, @RequestParam long playerId,
            @RequestParam(required = false) Long correctionOf, @RequestParam LocalDate observationDate,
            @RequestParam(required = false) String opponent, @RequestParam(required = false) String competition,
            @RequestParam Position observedPosition, @RequestParam(required = false) String strengths,
            @RequestParam(required = false) String weaknesses, @RequestParam(required = false) String comment,
            @RequestParam Map<String, String> params, Principal principal, Model model, RedirectAttributes redirect) {
        Map<AttributeType, Integer> scores = scores(params);
        try {
            long saved = evaluations.saveDraft(id, playerId, principal.getName(), observationDate, opponent, competition,
                    observedPosition, strengths, weaknesses, comment, scores, correctionOf);
            redirect.addFlashAttribute("success", "Borrador guardado"); return "redirect:/evaluations/" + saved + "/edit";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage()); prepare(model, playerId); return "evaluations/form";
        }
    }

    @PostMapping("/evaluations/{id}/confirm")
    String confirm(@PathVariable long id, Principal principal, RedirectAttributes redirect) {
        try { var evaluation = evaluations.find(id); evaluations.confirm(id, principal.getName());
            redirect.addFlashAttribute("success", "Evaluación confirmada e incorporada al perfil"); return "redirect:/players/" + evaluation.playerId();
        } catch (IllegalArgumentException exception) { redirect.addFlashAttribute("error", exception.getMessage()); return "redirect:/evaluations"; }
    }

    private void prepare(Model model, long playerId) {
        model.addAttribute("player", players.find(playerId)); model.addAttribute("attributes", evaluations.attributes());
    }

    private Map<AttributeType, Integer> scores(Map<String, String> params) {
        Map<AttributeType, Integer> scores = new EnumMap<>(AttributeType.class);
        for (AttributeType type : AttributeType.values()) {
            String value = params.get("score_" + type.name());
            if (value != null && !value.isBlank()) scores.put(type, Integer.parseInt(value));
        }
        return scores;
    }
}
