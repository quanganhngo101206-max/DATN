package com.skysport.datn.controller;

import com.skysport.datn.entity.Account;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

//@Controller
public class HomeController {

    @GetMapping("/home")
    public String home(HttpSession session, Model model) {
        // KhÃƒÂ´ng cÃ¡ÂºÂ§n Ã„â€˜Ã„Æ’ng nhÃ¡ÂºÂ­p vÃ¡ÂºÂ«n vÃƒÂ o Ã„â€˜Ã†Â°Ã¡Â»Â£c
        Account account = (Account) session.getAttribute("account");
        model.addAttribute("account", account); // null nÃ¡ÂºÂ¿u chÃ†Â°a Ã„â€˜Ã„Æ’ng nhÃ¡ÂºÂ­p
        return "home";
    }
}
