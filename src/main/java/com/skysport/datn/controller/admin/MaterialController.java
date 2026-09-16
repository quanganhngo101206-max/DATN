package com.skysport.datn.controller.admin;

import com.skysport.datn.entity.Material;
import com.skysport.datn.service.MaterialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

// MaterialController
@Controller
@RequestMapping("/admin/material")
public class MaterialController {
    @Autowired
    private MaterialService materialService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("materials", materialService.findAll());
        model.addAttribute("material", new Material());
        return "admin/material/list";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Material material) {
        materialService.save(material);
        return "redirect:/admin/material";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Integer id, Model model) {
        model.addAttribute("material", materialService.findById(id));
        model.addAttribute("materials", materialService.findAll());
        model.addAttribute("openModal", true);
        return "admin/material/list";
    }

    @PostMapping("/update")
    public String update(@ModelAttribute Material material) {
        materialService.update(material);
        return "redirect:/admin/material";
    }

    @PostMapping("/toggle-status/{id}")
    public String toggleStatus(@PathVariable Integer id) {
        materialService.toggleStatus(id);
        return "redirect:/admin/material";
    }
}