package com.skysport.datn.controller.customer;

import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.Bill;
import com.skysport.datn.entity.OrderStatusHistory;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.repository.BillRepository;
import com.skysport.datn.repository.OrderStatusHistoryRepository;
import com.skysport.datn.service.DiscountCodeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import org.springframework.transaction.annotation.Transactional;

@Controller
public class MockVNPayController {

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private DiscountCodeService discountCodeService;

    /**
     * Kiểm tra người đang thao tác có đúng là chủ đơn hàng không.
     * Áp dụng cùng logic với CheckoutController.orderSuccess():
     * - Khách đã đăng nhập: so khớp account của Customer gắn với Bill.
     * - Khách vãng lai (guest): chỉ được thao tác nếu billId trùng với
     *   "lastOrderId" vừa lưu vào session ngay sau khi đặt hàng.
     */
    private boolean isOwner(Bill bill, HttpSession session) {
        if (bill == null) return false;

        Account account = (Account) session.getAttribute("account");
        boolean ownedByAccount = account != null
                && bill.getCustomer() != null
                && bill.getCustomer().getAccount() != null
                && account.getId().equals(bill.getCustomer().getAccount().getId());

        Integer lastOrderId = (Integer) session.getAttribute("lastOrderId");
        boolean ownedByGuestSession = bill.getId().equals(lastOrderId);

        return ownedByAccount || ownedByGuestSession;
    }

    @GetMapping("/mock-vnpay")
    public String showMockVNPay(@RequestParam("billId") Integer billId,
                                @RequestParam("amount") Long amount,
                                HttpSession session,
                                Model model) {
        Bill bill = billRepository.findById(billId).orElse(null);
        if (bill == null) return "redirect:/home";

        if (!isOwner(bill, session)) {
            return "redirect:/home";
        }

        model.addAttribute("bill", bill);
        model.addAttribute("amount", amount);
        return "customer/vnpay/mock-vnpay";
    }

    @Transactional
    @PostMapping("/mock-vnpay-pay")
    public String processMockPayment(@RequestParam("billId") Integer billId,
                                     @RequestParam("otp") String otp,
                                     HttpSession session,
                                     org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        Bill bill = billRepository.findById(billId).orElse(null);
        if (bill == null) return "redirect:/home";

        if (!isOwner(bill, session)) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    "Bạn không có quyền thao tác trên đơn hàng này!"
            );
            return "redirect:/home";
        }

        if (!OrderStatus.PENDING.matches(bill.getStatus())) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    "Đơn hàng đã được xử lý, không thể thanh toán lại!"
            );
            return "redirect:/home";
        }

        // Giả lập thanh toán thành công
        bill.setStatus(OrderStatus.CONFIRMED.getValue());
        billRepository.save(bill);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setBill(bill);
        history.setStatus(OrderStatus.CONFIRMED.getValue());
        history.setNote("Đã thanh toán online qua VNPay (Mock)");
        history.setCreatedDate(LocalDateTime.now());
        orderStatusHistoryRepository.save(history);

        // VNPay: chỉ tính lượt voucher sau khi thanh toán thành công
        if (bill.getDiscountCode() != null) {
            discountCodeService.incrementUsage(
                    bill.getDiscountCode().getId()
            );
        }

        // Sinh mã giao dịch ngẫu nhiên
        String transactionNo = String.valueOf(10000000 + new Random().nextInt(90000000));
        String payDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"));

        redirectAttributes.addAttribute("billId", billId);
        redirectAttributes.addAttribute("transactionNo", transactionNo);
        redirectAttributes.addAttribute("payDate", payDate);

        return "redirect:/mock-vnpay-success";
    }

    @GetMapping("/mock-vnpay-success")
    public String showMockVNPaySuccess(@RequestParam("billId") Integer billId,
                                       @RequestParam("transactionNo") String transactionNo,
                                       @RequestParam("payDate") String payDate,
                                       HttpSession session,
                                       Model model) {
        Bill bill = billRepository.findById(billId).orElse(null);
        if (bill == null) return "redirect:/home";

        if (!isOwner(bill, session)) {
            return "redirect:/home";
        }

        model.addAttribute("bill", bill);
        model.addAttribute("transactionNo", transactionNo);
        model.addAttribute("payDate", payDate);
        return "customer/vnpay/mock-vnpay-success";
    }
}