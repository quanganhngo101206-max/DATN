package com.skysport.datn.controller.staff;

import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.Staff;
import com.skysport.datn.repository.StaffRepository;
import com.skysport.datn.service.BillService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/staff/bill")
@RequiredArgsConstructor
public class StaffBillController {

    private final BillService billService;
    private final StaffRepository staffRepository;

    private static final int PAGE_SIZE = 10;

    @GetMapping
    public String list(@RequestParam(required = false) Integer status,
                       @RequestParam(defaultValue = "0") int page,
                       Model model, HttpSession session) {

        Account account = (Account) session.getAttribute("account");
        if (account != null) {
            Staff staff = staffRepository.findByAccountId(account.getId());
            model.addAttribute("staff", staff);
        }

        Page<?> billPage;
        if (status != null) {
            billPage = billService.findByStatusPaged(status, page, PAGE_SIZE);
            model.addAttribute("currentStatus", status);
        } else {
            billPage = billService.findAllPaged(page, PAGE_SIZE);
            model.addAttribute("currentStatus", -1);
        }

        model.addAttribute("bills", billPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", billPage.getTotalPages());
        model.addAttribute("totalItems", billPage.getTotalElements());
        return "staff/bill/list";
    }

    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Integer id, Model model, HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        if (account != null) {
            Staff staff = staffRepository.findByAccountId(account.getId());
            model.addAttribute("staff", staff);
        }
        var bill = billService.findById(id);
        if (bill == null) return "redirect:/staff/bill";
        model.addAttribute("bill", bill);
        model.addAttribute("details", billService.findDetailsByBillId(id));
        model.addAttribute("history", billService.findHistoryByBillId(id));
        return "staff/bill/detail";
    }

    @PostMapping("/update-status/{id}")
    public String updateStatus(@PathVariable Integer id,
                               @RequestParam(required = false) Integer newStatus,
                               @RequestParam(required = false) String note,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        Account account = (Account) session.getAttribute("account");
        boolean success = newStatus != null && billService.updateStatus(id, newStatus, note, account);
        if (success) {
            redirectAttributes.addFlashAttribute("successMsg", "Cập nhật trạng thái thành công!");
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", "Không thể chuyển sang trạng thái này!");
        }
        return "redirect:/staff/bill/detail/" + id;
    }
}