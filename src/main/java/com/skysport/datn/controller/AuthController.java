package com.skysport.datn.controller;

import com.skysport.datn.dto.RegisterRequest;
import com.skysport.datn.entity.Account;
import com.skysport.datn.service.AccountService;
import com.skysport.datn.service.RegisterService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

//@Controller
public class AuthController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private RegisterService registerService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // HiÃ¡Â»Æ’n thÃ¡Â»â€¹ trang Ã„â€˜Ã„Æ’ng nhÃ¡ÂºÂ­p
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    // XÃ¡Â»Â­ lÃƒÂ½ Ã„â€˜Ã„Æ’ng nhÃ¡ÂºÂ­p
    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam String type,
                        HttpSession session,
                        Model model) {

        Account account = accountService.findByUsername(username);

        // Ã¢Å“â€¦ DÃƒÂ¹ng passwordEncoder.matches thay vÃƒÂ¬ equals
        if (account == null || !passwordEncoder.matches(password, account.getPassword())) {
            model.addAttribute("error", "Sai tÃƒÂ i khoÃ¡ÂºÂ£n hoÃ¡ÂºÂ·c mÃ¡ÂºÂ­t khÃ¡ÂºÂ©u!");
            return "login";
        }

        if (account.getStatus() == 0) {
            model.addAttribute("error", "TÃƒÂ i khoÃ¡ÂºÂ£n Ã„â€˜ÃƒÂ£ bÃ¡Â»â€¹ khÃƒÂ³a!");
            return "login";
        }

        session.setAttribute("account", account);

        if (type.equals("admin")) {
            if (account.getRole().getName().equals("Admin")) {
                return "redirect:/admin/dashboard";
            } else if (account.getRole().getName().equals("NhÃƒÂ¢n viÃƒÂªn")) {
                return "redirect:/staff/dashboard";
            } else {
                model.addAttribute("error", "BÃ¡ÂºÂ¡n khÃƒÂ´ng cÃƒÂ³ quyÃ¡Â»Ân truy cÃ¡ÂºÂ­p!");
                return "login";
            }
        } else {
            if (account.getRole().getName().equals("KhÃƒÂ¡ch hÃƒÂ ng")) {
                return "redirect:/home";
            } else {
                model.addAttribute("error", "BÃ¡ÂºÂ¡n khÃƒÂ´ng cÃƒÂ³ quyÃ¡Â»Ân truy cÃ¡ÂºÂ­p!");
                return "login";
            }
        }
    }

    @GetMapping("/register")
    public String registerPage(Model model){

        model.addAttribute(
                "registerRequest",
                new RegisterRequest());

        return "auth/register";
    }

    @PostMapping("/register")
    public String register(
            @ModelAttribute RegisterRequest request){

        registerService.register(request);

        return "redirect:/login";
    }

    // Ã„ÂÃ„Æ’ng xuÃ¡ÂºÂ¥t
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
