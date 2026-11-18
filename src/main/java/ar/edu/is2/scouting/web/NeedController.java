package ar.edu.is2.scouting.web;

import ar.edu.is2.scouting.domain.player.AttributeType;
import ar.edu.is2.scouting.domain.player.EvaluationService;
import ar.edu.is2.scouting.domain.player.Position;
import ar.edu.is2.scouting.domain.search.CandidateService;
import ar.edu.is2.scouting.domain.search.ScoutingSearchService;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class NeedController {
    private final ScoutingSearchService searches;
    private final EvaluationService evaluations;
    private final CandidateService candidates;
    public NeedController(ScoutingSearchService searches, EvaluationService evaluations, CandidateService candidates) {
        this.searches = searches; this.evaluations = evaluations; this.candidates = candidates;
    }

    @GetMapping("/needs")
    String list(Model model) { model.addAttribute("needs", searches.findAll()); return "needs/list"; }

    @GetMapping("/needs/new")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'DIRECTOR')")
    String form(Model model) { prepare(model); return "needs/form"; }

    @GetMapping("/needs/{id}")
    String detail(@PathVariable long id, Model model) {
        model.addAttribute("need", searches.findNeed(id)); model.addAttribute("executions", searches.executions(id)); return "needs/detail";
    }

    @GetMapping("/needs/{id}/version")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'DIRECTOR')")
    String version(@PathVariable long id, Model model) {
        var need = searches.findNeed(id);
        model.addAttribute("need", need);
        model.addAttribute("selectedCriteria", need.criteria().stream().map(ScoutingSearchService.CriterionRow::attribute).toList());
        model.addAttribute("criterionTargets", need.criteria().stream().collect(java.util.stream.Collectors.toMap(ScoutingSearchService.CriterionRow::attribute, ScoutingSearchService.CriterionRow::target)));
        model.addAttribute("criterionWeights", need.criteria().stream().collect(java.util.stream.Collectors.toMap(ScoutingSearchService.CriterionRow::attribute, ScoutingSearchService.CriterionRow::weight)));
        model.addAttribute("criterionMinimums", need.criteria().stream().filter(c -> c.minimum() != null).collect(java.util.stream.Collectors.toMap(ScoutingSearchService.CriterionRow::attribute, ScoutingSearchService.CriterionRow::minimum)));
        prepare(model);
        return "needs/form";
    }

    @PostMapping({"/needs", "/needs/{id}/version"})
    @PreAuthorize("hasAnyRole('COORDINATOR', 'DIRECTOR')")
    String save(@PathVariable(required = false) Long id, @RequestParam String title, @RequestParam(required = false) String description,
            @RequestParam Position position, @RequestParam(required = false) String league,
            @RequestParam(required = false) Integer minimumAge, @RequestParam(required = false) Integer maximumAge,
            @RequestParam(required = false) String nationality, @RequestParam(required = false) String preferredFoot,
            @RequestParam(required = false) List<AttributeType> selectedAttributes, @RequestParam Map<String, String> params,
            Principal principal, Model model, RedirectAttributes redirect) {
        var input = new ScoutingSearchService.NeedInput(title, description, position, league, minimumAge, maximumAge,
                nationality, preferredFoot, parseCriteria(selectedAttributes, params));
        try {
            long needId;
            if (id == null) needId = searches.create(input, principal.getName());
            else { searches.addVersion(id, input, principal.getName()); needId = id; }
            redirect.addFlashAttribute("success", id == null ? "Necesidad creada" : "Nueva versión creada sin alterar el historial");
            return "redirect:/needs/" + needId;
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage()); model.addAttribute("input", input); prepare(model); return "needs/form";
        }
    }

    @PostMapping("/needs/{id}/execute")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'DIRECTOR')")
    String execute(@PathVariable long id, Principal principal, RedirectAttributes redirect) {
        try { long execution = searches.execute(id, principal.getName()); return "redirect:/rankings/" + execution; }
        catch (IllegalArgumentException exception) { redirect.addFlashAttribute("error", exception.getMessage()); return "redirect:/needs/" + id; }
    }

    @PostMapping("/needs/{id}/close")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'DIRECTOR')")
    String close(@PathVariable long id, RedirectAttributes redirect) {
        searches.close(id); redirect.addFlashAttribute("success", "Necesidad cerrada; el historial permanece disponible"); return "redirect:/needs/" + id;
    }

    @GetMapping("/rankings/{id}")
    String ranking(@PathVariable long id, Model model) { model.addAttribute("execution", searches.findExecution(id)); return "needs/ranking"; }

    @PostMapping("/rankings/results/{resultId}/select")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'DIRECTOR')")
    String select(@PathVariable long resultId, @RequestParam long executionId, Principal principal, RedirectAttributes redirect) {
        try { long candidate = candidates.select(resultId, principal.getName()); redirect.addFlashAttribute("success", "Jugador incorporado al seguimiento"); return "redirect:/candidates/" + candidate; }
        catch (IllegalArgumentException exception) { redirect.addFlashAttribute("error", exception.getMessage()); return "redirect:/rankings/" + executionId; }
    }

    private void prepare(Model model) { model.addAttribute("attributes", evaluations.attributes()); }
    private List<ScoutingSearchService.CriterionInput> parseCriteria(List<AttributeType> selected, Map<String, String> params) {
        if (selected == null) return List.of();
        List<ScoutingSearchService.CriterionInput> criteria = new ArrayList<>();
        for (AttributeType type : selected) {
            int target = Integer.parseInt(params.getOrDefault("target_" + type.name(), "7"));
            int weight = Integer.parseInt(params.getOrDefault("weight_" + type.name(), "3"));
            String minimum = params.get("minimum_" + type.name());
            criteria.add(new ScoutingSearchService.CriterionInput(type, target, weight,
                    minimum == null || minimum.isBlank() ? null : Integer.valueOf(minimum)));
        }
        return criteria;
    }
}
