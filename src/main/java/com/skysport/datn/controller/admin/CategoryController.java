package com.skysport.datn.controller.admin;

import com.skysport.datn.entity.Category;
import com.skysport.datn.service.CategoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // Danh sách
    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("category", new Category());
        return "admin/category/list";
    }

    // Thêm
    @PostMapping("/save")
    public String save(@ModelAttribute Category category) {
        categoryService.save(category);
        return "redirect:/admin/category";
    }

    // Form sửa
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Integer id, Model model) {
        model.addAttribute("category", categoryService.findById(id));
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("openModal", true);
        return "admin/category/list";
    }

    // Cập nhật
    @PostMapping("/update")
    public String update(@ModelAttribute Category category) {
        Category old = categoryService.findById(category.getId());
        old.setCode(category.getCode());
        old.setName(category.getName());
        old.setStatus(category.getStatus());
        categoryService.update(old);
        return "redirect:/admin/category";
    }

    // Bật/Tắt hoạt động — thay cho chức năng xóa
    @PostMapping("/toggle-status/{id}")
    public String toggleStatus(@PathVariable Integer id) {
        categoryService.toggleStatus(id);
        return "redirect:/admin/category";
    }
}