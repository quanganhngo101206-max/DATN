package com.skysport.datn.controller.customer;

import com.skysport.datn.entity.Bill;
import com.skysport.datn.entity.OrderStatusHistory;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.repository.BillRepository;
import com.skysport.datn.repository.OrderStatusHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Controller
public class MockVNPayController {

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @GetMapping("/mock-vnpay")
    public String showMockVNPay(@RequestParam("billId") Integer billId,
                                @RequestParam("amount") Long amount,
                                Model model) {
        Bill bill = billRepository.findById(billId).orElse(null);
        if (bill == null) return "redirect:/home";

        model.addAttribute("bill", bill);
        model.addAttribute("amount", amount);
        return "customer/vnpay/mock-vnpay";
    }

    @PostMapping("/mock-vnpay-pay")
    public String processMockPayment(@RequestParam("billId") Integer billId,
                                     @RequestParam("otp") String otp,
                                     org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        Bill bill = billRepository.findById(billId).orElse(null);
        if (bill == null) return "redirect:/home";

        // Giả lập thanh toán thành công
        bill.setStatus(OrderStatus.CONFIRMED.getValue());
        billRepository.save(bill);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setBill(bill);
        history.setStatus(OrderStatus.CONFIRMED.getValue());
        history.setNote("Đã thanh toán online qua VNPay (Mock)");
        history.setCreatedDate(LocalDateTime.now());
        orderStatusHistoryRepository.save(history);

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
                                       Model model) {
        Bill bill = billRepository.findById(billId).orElse(null);
        if (bill == null) return "redirect:/home";

        model.addAttribute("bill", bill);
        model.addAttribute("transactionNo", transactionNo);
        model.addAttribute("payDate", payDate);
        return "customer/vnpay/mock-vnpay-success";
    }
}
