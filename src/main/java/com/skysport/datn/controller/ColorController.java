package com.skysport.datn.controller;


import com.skysport.datn.entity.Color;
import com.skysport.datn.service.ColorService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

// ColorController
//@Controller
@RequestMapping("/admin/color")
@RequiredArgsConstructor
public class ColorController {
    private final ColorService colorService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("colors", colorService.findAll());
        model.addAttribute("color", new Color());
        return "admin/color/list";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Color color) {
        colorService.save(color);
        return "redirect:/admin/color";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Integer id, Model model) {
        model.addAttribute("color", colorService.findById(id));
        model.addAttribute("colors", colorService.findAll());
        return "admin/color/list";
    }

    @PostMapping("/update")
    public String update(@ModelAttribute Color color) {
        colorService.update(color);
        return "redirect:/admin/color";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Integer id) {
        colorService.delete(id);
        return "redirect:/admin/color";
    }
}