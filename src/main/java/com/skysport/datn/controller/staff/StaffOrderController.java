package com.skysport.datn.controller.staff;

import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.Bill;
import com.skysport.datn.service.StaffOrderService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/staff/order")
@RequiredArgsConstructor
public class StaffOrderController {

    private final StaffOrderService staffOrderService;

    // ===== Xử lý tạo đơn =====
    @PostMapping("/create")
    public String createOrder(
            @RequestParam(required = false) Integer customerId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerPhone,
            @RequestParam Integer paymentMethodId,
            @RequestParam(required = false) String discountCode,
            @RequestParam List<Integer> productDetailIds,
            @RequestParam List<Integer> quantities,
            HttpSession session,
            RedirectAttributes ra) {

        try {
            Account account = (Account) session.getAttribute("account");
            Integer accountId = account != null ? account.getId() : null;

            Bill bill = staffOrderService.createOrder(
                    customerId, customerName, customerPhone,
                    paymentMethodId, discountCode,
                    productDetailIds, quantities, accountId);

            ra.addFlashAttribute("successMsg", "Tạo đơn " + bill.getCode() + " thành công!");
            return "redirect:/staff/bill/detail/" + bill.getId();

        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Lỗi: " + e.getMessage());
            return "redirect:/staff/order/create";
        }
    }
}