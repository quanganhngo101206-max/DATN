package com.skysport.datn.controller.admin;


import com.skysport.datn.entity.Size;
import com.skysport.datn.service.SizeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
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

    // Bật/Tắt hoạt động — không còn chức năng thêm/sửa/xóa (theo yêu cầu trước đó)
    @PostMapping("/toggle-status/{id}")
    public String toggleStatus(@PathVariable Integer id) {
        sizeService.toggleStatus(id);
        return "redirect:/admin/size";
    }
}