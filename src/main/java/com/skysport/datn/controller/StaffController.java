package com.skysport.datn.controller;

import com.skysport.datn.entity.Staff;
import com.skysport.datn.service.StaffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

//@Controller
@RequestMapping("/admin/staff")
public class StaffController {

    @Autowired
    private StaffService staffService;

    // Danh sÃƒÂ¡ch nhÃƒÂ¢n viÃƒÂªn
    @GetMapping
    public String list(Model model) {
        model.addAttribute("staffs", staffService.findAll());
        model.addAttribute("staff", new Staff());
        return "admin/staff/list";
    }

    // ThÃƒÂªm nhÃƒÂ¢n viÃƒÂªn
    @PostMapping("/save")
    public String save(@ModelAttribute Staff staff,
                       @RequestParam String username,
                       @RequestParam String password) {
        staffService.save(staff, username, password);
        return "redirect:/admin/staff";
    }

    // Form sÃ¡Â»Â­a
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Integer id, Model model) {
        model.addAttribute("staff", staffService.findById(id));
        model.addAttribute("staffs", staffService.findAll());
        return "admin/staff/list";
    }

    // CÃ¡ÂºÂ­p nhÃ¡ÂºÂ­t
    @PostMapping("/update")
    public String update(@ModelAttribute Staff staff) {
        Staff old = staffService.findById(staff.getId());
        old.setCode(staff.getCode());
        old.setName(staff.getName());
        old.setStatus(staff.getStatus());
        staffService.update(old);
        return "redirect:/admin/staff";
    }

    // KhÃƒÂ³a/MÃ¡Â»Å¸ khÃƒÂ³a
    @GetMapping("/toggle/{id}")
    public String toggle(@PathVariable Integer id) {
        staffService.toggleStatus(id);
        return "redirect:/admin/staff";
    }
}
