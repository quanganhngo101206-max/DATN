package com.skysport.datn.controller.admin;

import com.skysport.datn.entity.DiscountCode;
import com.skysport.datn.service.DiscountCodeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import lombok.RequiredArgsConstructor;
import com.skysport.datn.exception.BusinessException;

@Controller
@RequestMapping("/admin/discount")
@RequiredArgsConstructor
public class DiscountCodeController {

    private final DiscountCodeService discountCodeService;

    @GetMapping
    public String list(@RequestParam(required = false) Integer status, Model model) {

        List<DiscountCode> discounts = (status != null)
                ? discountCodeService.findByStatus(status)
                : discountCodeService.findAll();

        if (discounts == null) {
            discounts = List.of();
        }

        // Xác định trạng thái thực tế để hiển thị trên giao diện
        for (DiscountCode discount : discounts) {
            discount.setDisplayStatus(
                    discountCodeService.getDisplayStatus(discount)
            );
        }

        model.addAttribute("discounts", discounts);
        model.addAttribute("discount", new DiscountCode());
        model.addAttribute("currentStatus", status != null ? status : -1);
        model.addAttribute("pendingCount",
                discountCodeService.findByStatus(0).size());

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
        try {
            String codeError = discountCodeService.validateCode(
                    discountCode.getCode(),
                    null
            );

            if (codeError != null) {
                throw new BusinessException(codeError);
            }

            discountCode.setStatus(1);
            discountCode.setDeleteFlag(false);
            discountCode.setUsedCount(0);

            discountCodeService.save(discountCode);

            ra.addFlashAttribute("successMsg", "Đã tạo mã giảm giá thành công.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Lỗi: " + e.getMessage());
        }

        return "redirect:/admin/discount";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Integer id, Model model, RedirectAttributes ra) {
        DiscountCode discount = discountCodeService.findById(id);

        if (discount == null) {
            ra.addFlashAttribute("errorMsg", "Không tìm thấy mã giảm giá!");
            return "redirect:/admin/discount";
        }

        model.addAttribute("discount", discount);
        model.addAttribute("discounts", discountCodeService.findAll());
        model.addAttribute("currentStatus", -1);
        model.addAttribute("pendingCount", discountCodeService.findByStatus(0).size());
        model.addAttribute("openModal", true);
        return "admin/discount/list";
    }

    @PostMapping("/update")
    public String update(@ModelAttribute DiscountCode discountCode, RedirectAttributes ra) {
        try {
            DiscountCode old = discountCodeService.findById(discountCode.getId());

            if (old == null) {
                throw new BusinessException("Không tìm thấy mã giảm giá!");
            }

            String codeError = discountCodeService.validateCode(
                    discountCode.getCode(),
                    discountCode.getId()
            );

            if (codeError != null) {
                throw new BusinessException(codeError);
            }

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

            // Chỉ cho phép Admin đặt trạng thái hợp lệ
            if (discountCode.getStatus() != null
                    && (discountCode.getStatus() == 1
                    || discountCode.getStatus() == 2)) {
                old.setStatus(discountCode.getStatus());
            }

            discountCodeService.update(old);

            ra.addFlashAttribute("successMsg", "Đã cập nhật mã giảm giá.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Lỗi: " + e.getMessage());
        }

        return "redirect:/admin/discount";
    }

    // ===== DUYỆT / TỪ CHỐI MÃ GIẢM GIÁ DO STAFF TẠO =====

    // Duyệt: status=0 → status=1 (active)
    @PostMapping("/approve/{id}")
    public String approve(@PathVariable Integer id, RedirectAttributes ra) {
        DiscountCode dc = discountCodeService.findById(id);

        if (dc == null || Boolean.TRUE.equals(dc.getDeleteFlag())) {
            ra.addFlashAttribute("errorMsg", "Không tìm thấy mã giảm giá!");
            return "redirect:/admin/discount";
        }

        if (dc.getStatus() != null && dc.getStatus() == 0) {
            try {
                dc.setStatus(1);
                discountCodeService.update(dc);

                ra.addFlashAttribute(
                        "successMsg",
                        "Đã duyệt mã giảm giá: " + dc.getCode()
                );
            } catch (Exception e) {
                ra.addFlashAttribute("errorMsg", "Lỗi: " + e.getMessage());
            }
        } else {
            ra.addFlashAttribute(
                    "errorMsg",
                    "Mã không ở trạng thái chờ duyệt."
            );
        }

        return "redirect:/admin/discount";
    }

    // Từ chối: status=0 → status=2 (tạm dừng)
    // Không xóa voucher để giữ lịch sử
    @PostMapping("/reject/{id}")
    public String reject(@PathVariable Integer id, RedirectAttributes ra) {
        DiscountCode dc = discountCodeService.findById(id);

        if (dc == null || Boolean.TRUE.equals(dc.getDeleteFlag())) {
            ra.addFlashAttribute("errorMsg", "Không tìm thấy mã giảm giá!");
            return "redirect:/admin/discount";
        }

        if (dc.getStatus() != null && dc.getStatus() == 0) {
            try {
                dc.setStatus(2);
                discountCodeService.update(dc);

                ra.addFlashAttribute(
                        "successMsg",
                        "Đã từ chối mã giảm giá: " + dc.getCode()
                                + ". Mã được giữ lại ở trạng thái tạm dừng."
                );
            } catch (Exception e) {
                ra.addFlashAttribute("errorMsg", "Lỗi: " + e.getMessage());
            }
        } else {
            ra.addFlashAttribute(
                    "errorMsg",
                    "Mã không ở trạng thái chờ duyệt."
            );
        }

        return "redirect:/admin/discount";
    }

    // Hoạt động → Tạm dừng
    // Tạm dừng → Hoạt động
    @PostMapping("/toggle-status/{id}")
    public String toggleStatus(@PathVariable Integer id, RedirectAttributes ra) {

        DiscountCode dc = discountCodeService.findById(id);

        if (dc == null || Boolean.TRUE.equals(dc.getDeleteFlag())) {
            ra.addFlashAttribute("errorMsg", "Không tìm thấy mã giảm giá!");
            return "redirect:/admin/discount";
        }

        if (dc.getStatus() != null && dc.getStatus() == 1) {
            dc.setStatus(2);

            ra.addFlashAttribute(
                    "successMsg",
                    "Đã tạm dừng mã giảm giá: " + dc.getCode()
            );
        } else if (dc.getStatus() != null && dc.getStatus() == 2) {
            dc.setStatus(1);

            ra.addFlashAttribute(
                    "successMsg",
                    "Đã kích hoạt mã giảm giá: " + dc.getCode()
            );
        } else {
            ra.addFlashAttribute(
                    "errorMsg",
                    "Mã đang chờ duyệt, không thể bật/tắt trạng thái."
            );

            return "redirect:/admin/discount";
        }

        try {
            discountCodeService.update(dc);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Lỗi: " + e.getMessage());
        }

        return "redirect:/admin/discount";
    }
}