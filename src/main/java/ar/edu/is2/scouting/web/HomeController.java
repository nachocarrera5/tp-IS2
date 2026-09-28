package ar.edu.is2.scouting.web;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    String home(Model model, Principal principal) {
        model.addAttribute("username", principal == null ? null : principal.getName());
        return "index";
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }
}

