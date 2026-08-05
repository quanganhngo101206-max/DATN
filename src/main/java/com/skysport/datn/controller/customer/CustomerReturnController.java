package com.skysport.datn.controller.customer;

import com.skysport.datn.entity.*;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class CustomerReturnController {

    @Autowired private BillRepository billRepository;
    @Autowired private BillDetailRepository billDetailRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private BillReturnRequestRepository requestRepository;
    @Autowired private BillReturnRequestDetailRepository requestDetailRepository;

    // Hiển thị form tạo yêu cầu trả hàng cho 1 đơn đã hoàn thành
    @GetMapping("/customer/order/return/{billId}")
    public String returnForm(@PathVariable Integer billId, HttpSession session, Model model) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) return "redirect:/login";

        Bill bill = billRepository.findById(billId).orElse(null);
        if (bill == null) return "redirect:/customer/orders";

        Customer customer = customerRepository.findByAccountId(account.getId());
        if (customer == null || bill.getCustomer() == null
                || !bill.getCustomer().getId().equals(customer.getId())) {
            return "redirect:/customer/orders";
        }

        // Chỉ cho yêu cầu trả hàng khi đơn đã hoàn thành (status = 7)
        if (!OrderStatus.COMPLETED.matches(bill.getStatus())) {
            return "redirect:/customer/order/detail/" + billId;
        }

        // Không cho tạo nhiều yêu cầu cho cùng 1 đơn nếu đang chờ duyệt hoặc đã được duyệt
        List<ReturnRequest> existing = requestRepository.findByBill_Id(billId);
        boolean hasActive = existing.stream().anyMatch(r -> r.getStatus() == 1 || r.getStatus() == 2);
        if (hasActive) {
            return "redirect:/customer/order/detail/" + billId;
        }

        List<BillDetail> details = billDetailRepository.findByBillId(billId);
        model.addAttribute("bill", bill);
        model.addAttribute("details", details);
        return "customer/order/return-request";
    }

    // Gửi yêu cầu trả hàng
    @Transactional
    @PostMapping("/customer/order/return/{billId}")
    public String submitReturn(@PathVariable Integer billId,
                               @RequestParam(required = false) List<Integer> billDetailIds,
                               @RequestParam(required = false) List<Integer> returnQuantities,
                               HttpSession session,
                               RedirectAttributes ra) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) return "redirect:/login";

        Bill bill = billRepository.findById(billId).orElse(null);
        if (bill == null) return "redirect:/customer/orders";

        Customer customer = customerRepository.findByAccountId(account.getId());
        if (customer == null || bill.getCustomer() == null
                || !bill.getCustomer().getId().equals(customer.getId())) {
            return "redirect:/customer/orders";
        }

        if (!OrderStatus.COMPLETED.matches(bill.getStatus())) {
            ra.addFlashAttribute("error", "Chỉ có thể yêu cầu trả hàng với đơn đã hoàn thành!");
            return "redirect:/customer/order/detail/" + billId;
        }

        List<ReturnRequest> existing = requestRepository.findByBill_Id(billId);
        boolean hasActive = existing.stream().anyMatch(r -> r.getStatus() == 1 || r.getStatus() == 2);
        if (hasActive) {
            ra.addFlashAttribute("error", "Đơn hàng này đã có yêu cầu trả hàng đang xử lý!");
            return "redirect:/customer/order/detail/" + billId;
        }

        if (billDetailIds == null || billDetailIds.isEmpty()) {
            ra.addFlashAttribute("error", "Vui lòng chọn ít nhất một sản phẩm để trả!");
            return "redirect:/customer/order/return/" + billId;
        }

        List<BillDetail> details = billDetailRepository.findByBillId(billId);

        ReturnRequest request = ReturnRequest.builder()
                .code("YCTH" + System.currentTimeMillis())
                .createdDate(LocalDateTime.now())
                .status(1) // 1 = chờ duyệt
                .bill(bill)
                .build();
        request = requestRepository.save(request);

        boolean anyItem = false;
        for (int i = 0; i < billDetailIds.size(); i++) {
            Integer billDetailId = billDetailIds.get(i);
            Integer qty = (returnQuantities != null && i < returnQuantities.size()) ? returnQuantities.get(i) : 0;
            if (qty == null || qty <= 0) continue;

            BillDetail bd = details.stream()
                    .filter(d -> d.getId().equals(billDetailId))
                    .findFirst().orElse(null);
            if (bd == null) continue;

            // Không cho trả nhiều hơn số lượng đã mua
            int maxQty = bd.getQuantity() != null ? bd.getQuantity() : 0;
            if (qty > maxQty) qty = maxQty;
            if (qty <= 0) continue;

            ReturnRequestDetail detail = ReturnRequestDetail.builder()
                    .billReturnRequest(request)
                    .productDetail(bd.getProductDetail())
                    .quantityReturn(qty)
                    .momentPriceRefund(bd.getMomentPrice())
                    .build();
            requestDetailRepository.save(detail);
            anyItem = true;
        }

        if (!anyItem) {
            requestRepository.delete(request);
            ra.addFlashAttribute("error", "Vui lòng chọn số lượng hợp lệ cho ít nhất một sản phẩm!");
            return "redirect:/customer/order/return/" + billId;
        }

        ra.addFlashAttribute("successMsg", "Đã gửi yêu cầu trả hàng " + request.getCode() + ". Vui lòng chờ xử lý.");
        return "redirect:/customer/order/detail/" + billId;
    }
}