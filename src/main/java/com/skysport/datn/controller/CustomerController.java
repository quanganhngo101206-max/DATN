package com.skysport.datn.controller;

import com.skysport.datn.service.CustomerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import lombok.RequiredArgsConstructor;

//@Controller
@RequestMapping("/admin/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    // Danh sÃƒÂ¡ch khÃƒÂ¡ch hÃƒÂ ng
    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isEmpty()) {
            model.addAttribute("customers", customerService.search(keyword));
            model.addAttribute("keyword", keyword);
        } else {
            model.addAttribute("customers", customerService.findAll());
        }
        return "admin/customer/list";
    }

    // Xem chi tiÃ¡ÂºÂ¿t
    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        model.addAttribute("customer", customerService.findById(id));
        return "admin/customer/detail";
    }

    // KhÃƒÂ³a/MÃ¡Â»Å¸ khÃƒÂ³a
    @GetMapping("/toggle/{id}")
    public String toggle(@PathVariable Integer id) {
        customerService.toggleStatus(id);
        return "redirect:/admin/customer";
    }
}