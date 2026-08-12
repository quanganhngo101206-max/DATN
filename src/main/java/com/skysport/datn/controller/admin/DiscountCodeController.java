package com.skysport.datn.controller.admin;

import com.skysport.datn.entity.DiscountCode;
import com.skysport.datn.service.DiscountCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/discount")
public class DiscountCodeController {

    @Autowired
    private DiscountCodeService discountCodeService;

    @GetMapping
    public String list(@RequestParam(required = false) Integer status, Model model) {
        List<DiscountCode> discounts = (status != null)
                ? discountCodeService.findByStatus(status)
                : discountCodeService.findAll();
        model.addAttribute("discounts", discounts != null ? discounts : List.of());
        model.addAttribute("discount", new DiscountCode());
        model.addAttribute("currentStatus", status != null ? status : -1);
        // Badge số mã chờ duyệt
        model.addAttribute("pendingCount", discountCodeService.findByStatus(0).size());
        return "admin/discount/list";
    }

    @GetMapping("/add")
    public String addForm(Model model) {
        model.addAttribute("discount", new DiscountCode());
        model.addAttribute("discounts", discountCodeService.findAll());
        model.addAttribute("currentStatus", -1);
        model.addAttribute("pendingCount", discountCodeService.findByStatus(0).size());
        model.addAttribute("openModal", true);
        return "admin/discount/list";
    }

    // Admin tạo mới → active ngay (status=1)
    @PostMapping("/save")
    public String save(@ModelAttribute DiscountCode discountCode, RedirectAttributes ra) {
        if (discountCode.getCode() == null || discountCode.getCode().isBlank()) {
            ra.addFlashAttribute("errorMsg", "Vui lòng nhập mã giảm giá!");
            return "redirect:/admin/discount";
        }
        if (discountCode.getStartDate() == null || discountCode.getEndDate() == null) {
            ra.addFlashAttribute("errorMsg", "Vui lòng nhập đầy đủ ngày bắt đầu và ngày kết thúc!");
            return "redirect:/admin/discount";
        }
        if (discountCode.getEndDate().isBefore(discountCode.getStartDate())) {
            ra.addFlashAttribute("errorMsg", "Ngày kết thúc phải sau ngày bắt đầu!");
            return "redirect:/admin/discount";
        }
        discountCode.setStatus(1);
        discountCode.setDeleteFlag(false);
        discountCodeService.save(discountCode);
        ra.addFlashAttribute("successMsg", "Đã tạo mã giảm giá thành công.");
        return "redirect:/admin/discount";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Integer id, Model model) {
        model.addAttribute("discount", discountCodeService.findById(id));
        model.addAttribute("discounts", discountCodeService.findAll());
        model.addAttribute("currentStatus", -1);
        model.addAttribute("pendingCount", discountCodeService.findByStatus(0).size());
        model.addAttribute("openModal", true);
        return "admin/discount/list";
    }

    @PostMapping("/update")
    public String update(@ModelAttribute DiscountCode discountCode, RedirectAttributes ra) {
        if (discountCode.getStartDate() == null || discountCode.getEndDate() == null) {
            ra.addFlashAttribute("errorMsg", "Vui lòng nhập đầy đủ ngày bắt đầu và ngày kết thúc!");
            return "redirect:/admin/discount";
        }
        if (discountCode.getEndDate().isBefore(discountCode.getStartDate())) {
            ra.addFlashAttribute("errorMsg", "Ngày kết thúc phải sau ngày bắt đầu!");
            return "redirect:/admin/discount";
        }
        DiscountCode old = discountCodeService.findById(discountCode.getId());
        if (old != null) {
            old.setCode(discountCode.getCode());
            old.setDetail(discountCode.getDetail());
            old.setType(discountCode.getType());
            old.setDiscountAmount(discountCode.getDiscountAmount());
            old.setPercentage(discountCode.getPercentage());
            old.setMinimumAmountInCart(discountCode.getMinimumAmountInCart());
            old.setMaximumAmount(discountCode.getMaximumAmount());
            old.setMaximumUsage(discountCode.getMaximumUsage());
            old.setStartDate(discountCode.getStartDate());
            old.setEndDate(discountCode.getEndDate());
            old.setStatus(discountCode.getStatus());
            discountCodeService.update(old);
            ra.addFlashAttribute("successMsg", "Đã cập nhật mã giảm giá.");
        }
        return "redirect:/admin/discount";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Integer id, RedirectAttributes ra) {
        discountCodeService.delete(id);
        ra.addFlashAttribute("successMsg", "Đã xóa mã giảm giá.");
        return "redirect:/admin/discount";
    }

    // ===== DUYỆT / TỪ CHỐI MÃ GIẢM GIÁ DO STAFF TẠO =====

    // Duyệt: status=0 → status=1 (active)
    @PostMapping("/approve/{id}")
    public String approve(@PathVariable Integer id, RedirectAttributes ra) {
        DiscountCode dc = discountCodeService.findById(id);
        if (dc != null && dc.getStatus() == 0) {
            dc.setStatus(1);
            discountCodeService.update(dc);
            ra.addFlashAttribute("successMsg", "Đã duyệt mã giảm giá: " + dc.getCode());
        } else {
            ra.addFlashAttribute("errorMsg", "Mã không ở trạng thái chờ duyệt.");
        }
        return "redirect:/admin/discount";
    }

    // Từ chối: status=0 → deleteFlag=true (xóa mềm)
    @PostMapping("/reject/{id}")
    public String reject(@PathVariable Integer id, RedirectAttributes ra) {
        DiscountCode dc = discountCodeService.findById(id);
        if (dc != null && dc.getStatus() == 0) {
            dc.setDeleteFlag(true);
            discountCodeService.update(dc);
            ra.addFlashAttribute("successMsg", "Đã từ chối mã giảm giá: " + dc.getCode());
        } else {
            ra.addFlashAttribute("errorMsg", "Mã không ở trạng thái chờ duyệt.");
        }
        return "redirect:/admin/discount";
    }
}