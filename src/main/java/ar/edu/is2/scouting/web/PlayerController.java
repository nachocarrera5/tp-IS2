package ar.edu.is2.scouting.web;

import ar.edu.is2.scouting.domain.player.EvaluationService;
import ar.edu.is2.scouting.domain.player.CsvImportService;
import ar.edu.is2.scouting.domain.player.PlayerService;
import ar.edu.is2.scouting.domain.player.Position;
import ar.edu.is2.scouting.domain.search.CandidateService;
import jakarta.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PlayerController {
    private final PlayerService players;
    private final EvaluationService evaluations;
    private final CsvImportService csvImports;
    private final CandidateService candidates;
    public PlayerController(PlayerService players, EvaluationService evaluations, CsvImportService csvImports,
            CandidateService candidates) {
        this.players = players; this.evaluations = evaluations; this.csvImports = csvImports; this.candidates = candidates;
    }

    @GetMapping("/players")
    String list(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("players", players.findAll(q)); model.addAttribute("q", q);
        model.addAttribute("followedPlayerIds", candidates.followedPlayerIds());
        return "players/list";
    }

    @GetMapping("/players/{id}")
    String detail(@PathVariable long id, Model model) {
        model.addAttribute("player", players.find(id)); model.addAttribute("evaluations", evaluations.forPlayer(id)); return "players/detail";
    }

    @PreAuthorize("hasRole('COORDINATOR')")
    @GetMapping({"/players/new", "/players/{id}/edit"})
    String form(@PathVariable(required = false) Long id, Model model) {
        if (id != null) model.addAttribute("player", players.find(id));
        return "players/form";
    }

    @PreAuthorize("hasRole('COORDINATOR')")
    @PostMapping({"/players", "/players/{id}"})
    String save(@PathVariable(required = false) Long id, @RequestParam(required = false) String externalId,
            @RequestParam String fullName, @RequestParam LocalDate birthDate, @RequestParam(required = false) String nationality,
            @RequestParam(required = false) String club, @RequestParam(required = false) String league,
            @RequestParam(required = false) String preferredFoot, @RequestParam Position primaryPosition,
            @RequestParam(required = false) List<Position> secondaryPositions, Model model, RedirectAttributes redirect) {
        var input = new PlayerService.PlayerInput(externalId, fullName, birthDate, nationality, club, league,
                preferredFoot, primaryPosition, secondaryPositions == null ? List.of() : secondaryPositions);
        try {
            long saved = players.save(id, input); redirect.addFlashAttribute("success", "Jugador guardado correctamente");
            return "redirect:/players/" + saved;
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage()); model.addAttribute("input", input); return "players/form";
        }
    }

    public record CsvPreviewRow(int row, PlayerService.PlayerInput player, Long duplicateId, String error) {}

    @PreAuthorize("hasRole('COORDINATOR')")
    @GetMapping("/players/import")
    String importForm() { return "players/import"; }

    @PreAuthorize("hasRole('COORDINATOR')")
    @PostMapping("/players/import/preview")
    String preview(@RequestParam MultipartFile file, Model model, HttpSession session) throws Exception {
        List<CsvPreviewRow> rows = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line; int row = 0;
            while ((line = reader.readLine()) != null) {
                row++; if (row == 1 && line.toLowerCase().contains("full_name")) continue;
                try {
                    var player = players.parseCsvRow(line.split(",", -1));
                    rows.add(new CsvPreviewRow(row, player, players.findDuplicate(player).orElse(null), null));
                } catch (Exception exception) { rows.add(new CsvPreviewRow(row, null, null, exception.getMessage())); }
            }
        }
        session.setAttribute("csvPreview", rows);
        session.setAttribute("csvFileName", file.getOriginalFilename());
        model.addAttribute("rows", rows); model.addAttribute("fileName", file.getOriginalFilename());
        return "players/import";
    }

    @SuppressWarnings("unchecked")
    @PreAuthorize("hasRole('COORDINATOR')")
    @PostMapping("/players/import/confirm")
    String confirmImport(HttpSession session, @RequestParam Map<String, String> parameters,
            Principal principal, RedirectAttributes redirect) {
        List<CsvPreviewRow> rows = (List<CsvPreviewRow>) session.getAttribute("csvPreview");
        if (rows == null) { redirect.addFlashAttribute("error", "La vista previa venció"); return "redirect:/players/import"; }
        int imported = 0, rejected = 0;
        List<CsvImportService.ImportError> errors = new ArrayList<>();
        for (CsvPreviewRow row : rows) {
            if (row.error() != null) {
                rejected++; errors.add(new CsvImportService.ImportError(row.row(), row.error())); continue;
            }
            if (row.duplicateId() != null && !"UPDATE".equals(parameters.get("decision_" + row.row()))) {
                rejected++; errors.add(new CsvImportService.ImportError(row.row(), "Duplicado omitido por el coordinador")); continue;
            }
            try {
                players.save(row.duplicateId(), row.player()); imported++;
            } catch (IllegalArgumentException exception) {
                rejected++; errors.add(new CsvImportService.ImportError(row.row(), exception.getMessage()));
            }
        }
        csvImports.record((String) session.getAttribute("csvFileName"), rows.size(), imported, rejected,
                errors, principal.getName());
        session.removeAttribute("csvPreview"); session.removeAttribute("csvFileName");
        redirect.addFlashAttribute("success", "Importación finalizada: " + imported + " incorporados o actualizados, " + rejected + " omitidos o rechazados");
        return "redirect:/players";
    }
}
