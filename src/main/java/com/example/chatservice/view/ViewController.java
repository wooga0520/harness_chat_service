package com.example.chatservice.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ViewController {

    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    @GetMapping("/rooms")
    public String rooms() {
        return "rooms";
    }

    @GetMapping("/rooms/{roomId}")
    public String room(@PathVariable Long roomId, Model model) {
        model.addAttribute("roomId", roomId);
        return "room";
    }
}
