package ar.edu.is2.scouting.web;

import ar.edu.is2.scouting.domain.user.UserAccountService;
import ar.edu.is2.scouting.domain.user.UserRole;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private final UserAccountService users;
    public AuthController(UserAccountService users) { this.users = users; }

    @GetMapping("/register")
    String form(Model model) { model.addAttribute("roles", UserRole.values()); return "register"; }

    @PostMapping("/register")
    String register(@RequestParam String fullName, @RequestParam String email, @RequestParam String password,
            @RequestParam UserRole role, Model model, RedirectAttributes redirect) {
        try {
            users.register(fullName, email, password, role);
            redirect.addFlashAttribute("success", "Cuenta creada. Ya podés ingresar.");
            return "redirect:/login";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage()); model.addAttribute("roles", UserRole.values());
            model.addAttribute("fullName", fullName); model.addAttribute("email", email); return "register";
        }
    }
}
