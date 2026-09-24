package com.skysport.datn.controller.admin;

import com.skysport.datn.entity.Brand;
import com.skysport.datn.service.BrandService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

// BrandController
@Controller
@RequestMapping("/admin/brand")
@RequiredArgsConstructor
public class BrandController {
    private final BrandService brandService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("brands", brandService.findAll());
        model.addAttribute("brand", new Brand());
        return "admin/brand/list";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Brand brand) {
        brandService.save(brand);
        return "redirect:/admin/brand";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Integer id, Model model) {
        model.addAttribute("brand", brandService.findById(id));
        model.addAttribute("brands", brandService.findAll());
        model.addAttribute("openModal", true);
        return "admin/brand/list";
    }

    @PostMapping("/update")
    public String update(@ModelAttribute Brand brand) {
        brandService.update(brand);
        return "redirect:/admin/brand";
    }

    @PostMapping("/toggle-status/{id}")
    public String toggleStatus(@PathVariable Integer id) {
        brandService.toggleStatus(id);
        return "redirect:/admin/brand";
    }
}