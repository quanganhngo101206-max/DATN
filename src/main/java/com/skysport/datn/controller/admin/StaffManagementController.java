package com.skysport.datn.controller.admin;

import com.skysport.datn.entity.Staff;
import com.skysport.datn.service.StaffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/staff")
public class StaffManagementController {

    @Autowired
    private StaffService staffService;

    // Danh sách nhân viên
    @GetMapping
    public String list(Model model) {
        model.addAttribute("staffs", staffService.findAll());
        model.addAttribute("staff", new Staff());
        return "admin/staff/list";
    }

    // Thêm nhân viên
    @PostMapping("/save")
    public String save(@ModelAttribute Staff staff,
                       @RequestParam String username,
                       @RequestParam String password,
                       RedirectAttributes ra) {
        try {
            staffService.save(staff, username, password);
            ra.addFlashAttribute("successMsg", "Đã thêm nhân viên thành công.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/staff";
    }

    // Form sửa
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Integer id, Model model) {
        model.addAttribute("staff", staffService.findById(id));
        model.addAttribute("staffs", staffService.findAll());
        return "admin/staff/list";
    }

    // Cập nhật
    @PostMapping("/update")
    public String update(@ModelAttribute Staff staff) {
        Staff old = staffService.findById(staff.getId());
        old.setCode(staff.getCode());
        old.setName(staff.getName());
        old.setStatus(staff.getStatus());
        old.setPhoneNumber(staff.getPhoneNumber());
        old.setEmail(staff.getEmail());
        old.setGender(staff.getGender());
        old.setAddress(staff.getAddress());
        staffService.update(old);
        return "redirect:/admin/staff";
    }

    // Khóa/Mở khóa
    @GetMapping("/toggle/{id}")
    public String toggle(@PathVariable Integer id) {
        staffService.toggleStatus(id);
        return "redirect:/admin/staff";
    }

    // Mở khóa tài khoản bị auto-lock do đăng nhập sai quá nhiều lần
    @GetMapping("/unlock/{id}")
    public String unlock(@PathVariable Integer id, RedirectAttributes ra) {
        staffService.unlockAccount(id);
        ra.addFlashAttribute("successMsg", "Đã mở khóa tài khoản.");
        return "redirect:/admin/staff";
    }
}