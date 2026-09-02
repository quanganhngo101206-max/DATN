package com.skysport.datn.controller;


import com.skysport.datn.entity.Size;
import com.skysport.datn.service.SizeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

//@Controller
@RequestMapping("/admin/size")
public class SizeController {

    @Autowired
    private SizeService sizeService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("sizes", sizeService.findAll());
        model.addAttribute("size", new Size());
        return "admin/size/list";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Size size) {
        sizeService.save(size);
        return "redirect:/admin/size";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Integer id, Model model) {
        model.addAttribute("size", sizeService.findById(id));
        model.addAttribute("sizes", sizeService.findAll());
        return "admin/size/list";
    }

    @PostMapping("/update")
    public String update(@ModelAttribute Size size) {
        sizeService.update(size);
        return "redirect:/admin/size";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Integer id) {
        sizeService.delete(id);
        return "redirect:/admin/size";
    }
}
