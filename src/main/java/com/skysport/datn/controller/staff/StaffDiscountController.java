package com.skysport.datn.controller.staff;

import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.DiscountCode;
import com.skysport.datn.entity.Staff;
import com.skysport.datn.repository.StaffRepository;
import com.skysport.datn.service.DiscountCodeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/staff/discount")
@RequiredArgsConstructor
public class StaffDiscountController {

    private final DiscountCodeService discountCodeService;

    private final StaffRepository staffRepository;

    @GetMapping
    public String list(Model model, HttpSession session) {

        Account account = (Account) session.getAttribute("account");
        if (account != null) {
            Staff staff = staffRepository.findByAccountId(account.getId());
            model.addAttribute("staff", staff);
        }

        model.addAttribute("discounts", discountCodeService.findAll());
        model.addAttribute("discount", new DiscountCode());
        return "staff/discount/list";
    }

    @GetMapping("/add")
    public String addForm(Model model) {
        model.addAttribute("discount", new DiscountCode());
        model.addAttribute("discounts", discountCodeService.findAll());
        model.addAttribute("openModal", true);
        return "staff/discount/list";
    }

    // Staff tạo mới → status=0 (chờ Admin duyệt), deleteFlag=false
    @PostMapping("/save")
    public String save(@ModelAttribute DiscountCode discountCode, RedirectAttributes ra) {

        if (discountCode.getCode() == null || discountCode.getCode().isBlank()) {
            ra.addFlashAttribute("errorMsg", "Vui lòng nhập mã giảm giá!");
            return "redirect:/staff/discount";
        }

        String codeError = discountCodeService.validateCode(discountCode.getCode(), null);
        if (codeError != null) {
            ra.addFlashAttribute("errorMsg", codeError);
            return "redirect:/staff/discount";
        }

        if (discountCode.getStartDate() == null || discountCode.getEndDate() == null) {
            ra.addFlashAttribute("errorMsg", "Vui lòng nhập đầy đủ ngày bắt đầu và ngày kết thúc!");
            return "redirect:/staff/discount";
        }

        if (discountCode.getEndDate().isBefore(discountCode.getStartDate())) {
            ra.addFlashAttribute("errorMsg", "Ngày kết thúc phải sau ngày bắt đầu!");
            return "redirect:/staff/discount";
        }

        discountCode.setStatus(0);       // 0 = chờ duyệt
        discountCode.setDeleteFlag(false);
        discountCode.setUsedCount(0);

        try {
            discountCodeService.save(discountCode);
            ra.addFlashAttribute("successMsg", "Đã gửi yêu cầu tạo mã giảm giá. Chờ Admin duyệt.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }

        return "redirect:/staff/discount";
    }

    // Staff chỉ edit được discount đang ở trạng thái chờ duyệt (status=0)
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Integer id, Model model, RedirectAttributes ra) {
        DiscountCode dc = discountCodeService.findById(id);
        if (dc == null || dc.getStatus() != 0) {
            ra.addFlashAttribute("errorMsg", "Chỉ có thể chỉnh sửa mã đang chờ duyệt.");
            return "redirect:/staff/discount";
        }
        model.addAttribute("discount", dc);
        model.addAttribute("discounts", discountCodeService.findAll());
        model.addAttribute("openModal", true);
        return "staff/discount/list";
    }

    @PostMapping("/update")
    public String update(@ModelAttribute DiscountCode discountCode, RedirectAttributes ra) {

        if (discountCode.getId() == null) {
            ra.addFlashAttribute("errorMsg", "Mã giảm giá không hợp lệ.");
            return "redirect:/staff/discount";
        }

        DiscountCode old = discountCodeService.findById(discountCode.getId());
        if (old == null || old.getStatus() != 0) {
            ra.addFlashAttribute("errorMsg", "Không thể chỉnh sửa mã này.");
            return "redirect:/staff/discount";
        }

        if (discountCode.getCode() == null || discountCode.getCode().isBlank()) {
            ra.addFlashAttribute("errorMsg", "Vui lòng nhập mã giảm giá!");
            return "redirect:/staff/discount";
        }

        String codeError = discountCodeService.validateCode(
                discountCode.getCode(),
                discountCode.getId()
        );
        if (codeError != null) {
            ra.addFlashAttribute("errorMsg", codeError);
            return "redirect:/staff/discount";
        }

        if (discountCode.getStartDate() == null || discountCode.getEndDate() == null) {
            ra.addFlashAttribute("errorMsg", "Vui lòng nhập đầy đủ ngày bắt đầu và ngày kết thúc!");
            return "redirect:/staff/discount";
        }

        if (discountCode.getEndDate().isBefore(discountCode.getStartDate())) {
            ra.addFlashAttribute("errorMsg", "Ngày kết thúc phải sau ngày bắt đầu!");
            return "redirect:/staff/discount";
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

        old.setStatus(0);       // Sửa xong vẫn phải chờ Admin duyệt
        old.setDeleteFlag(false);

        try {
            discountCodeService.update(old);
            ra.addFlashAttribute("successMsg", "Đã cập nhật. Mã vẫn đang chờ Admin duyệt.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }

        return "redirect:/staff/discount";
    }
}