package com.skysport.datn.controller.customer;

import com.skysport.datn.entity.Bill;
import com.skysport.datn.entity.OrderStatusHistory;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.repository.BillRepository;
import com.skysport.datn.repository.OrderStatusHistoryRepository;
import com.skysport.datn.service.BillService;
import com.skysport.datn.service.DiscountCodeService;
import com.skysport.datn.service.VNPayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * Xử lý kết quả thanh toán thật từ VNPay sandbox.
 * <p>
 * - /vnpay-return : VNPay redirect TRÌNH DUYỆT của khách về đây sau khi thanh toán.
 * Có thể bị khách đóng tab / mất mạng giữa chừng nên KHÔNG nên là nguồn xác nhận
 * duy nhất cho những hệ thống chạy thật — nhưng với đồ án chạy sandbox không có
 * domain public để nhận IPN thì đây vẫn là nơi cập nhật trạng thái đơn hàng chính.
 * <p>
 * - /vnpay-ipn : VNPay gọi SERVER-TO-SERVER (không qua trình duyệt khách) để xác nhận
 * độc lập, đúng chuẩn production. Muốn test được cần khai báo IPN URL là một domain
 * public (vd. link ngrok) trong trang quản trị merchant sandbox, vì VNPay không gọi
 * được vào localhost của máy bạn.
 * <p>
 * Cả hai endpoint dùng chung VNPayService.verify() để không lặp logic xác thực chữ ký.
 */
@Controller
@RequiredArgsConstructor
public class VNPayController {

    private final VNPayService vnPayService;
    private final BillRepository billRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final DiscountCodeService discountCodeService;
    private final BillService billService;

    @Transactional
    @GetMapping("/vnpay-return")
    public String vnpayReturn(HttpServletRequest request, HttpSession session, Model model) {
        VNPayService.VerifyResult result = vnPayService.verify(request);

        switch (result.status()) {
            case INVALID_SIGNATURE -> {
                model.addAttribute("success", false);
                model.addAttribute("bill", null);
                model.addAttribute("errorMsg", "Chữ ký không hợp lệ, giao dịch có thể đã bị can thiệp.");
                return "customer/vnpay/vnpay-result";
            }
            case ORDER_NOT_FOUND -> {
                return "redirect:/home";
            }
            case AMOUNT_MISMATCH -> {
                model.addAttribute("success", false);
                model.addAttribute("bill", result.bill());
                model.addAttribute("errorMsg", "Số tiền không khớp với hóa đơn, giao dịch bị từ chối.");
                return "customer/vnpay/vnpay-result";
            }
            default -> { /* SUCCESS hoặc FAILED -> xử lý bên dưới */ }
        }

        Bill bill = result.bill();
        applyPaymentResult(bill, result);

        model.addAttribute("bill", bill);
        model.addAttribute("success", result.status() == VNPayService.VerifyStatus.SUCCESS);
        model.addAttribute("transactionNo", result.transactionNo());
        model.addAttribute("payDate", result.payDate());
        return "customer/vnpay/vnpay-result";
    }

    /**
     * IPN: PHẢI trả về đúng format JSON VNPay quy định, không phải trang HTML.
     * Mã RspCode chuẩn: "00" = Confirm Success, "01" = Order not found,
     * "02" = Order already confirmed, "04" = Invalid amount, "97" = Invalid signature.
     */
    @Transactional
    @GetMapping("/vnpay-ipn")
    @ResponseBody
    public ResponseEntity<Map<String, String>> vnpayIpn(HttpServletRequest request) {
        VNPayService.VerifyResult result = vnPayService.verify(request);

        Map<String, String> resp = new LinkedHashMap<>();

        switch (result.status()) {
            case INVALID_SIGNATURE -> {
                resp.put("RspCode", "97");
                resp.put("Message", "Invalid signature");
                return ResponseEntity.ok(resp);
            }
            case ORDER_NOT_FOUND -> {
                resp.put("RspCode", "01");
                resp.put("Message", "Order not found");
                return ResponseEntity.ok(resp);
            }
            case AMOUNT_MISMATCH -> {
                resp.put("RspCode", "04");
                resp.put("Message", "Invalid amount");
                return ResponseEntity.ok(resp);
            }
            default -> { /* SUCCESS hoặc FAILED */ }
        }

        Bill bill = result.bill();

        // Idempotency: nếu đơn không còn ở PENDING (đã được /vnpay-return xử lý trước,
        // hoặc VNPay gọi IPN trùng lặp) thì báo "đã xác nhận", không xử lý lại.
        if (!OrderStatus.PENDING.matches(bill.getStatus())) {
            resp.put("RspCode", "02");
            resp.put("Message", "Order already confirmed");
            return ResponseEntity.ok(resp);
        }

        applyPaymentResult(bill, result);

        resp.put("RspCode", "00");
        resp.put("Message", "Confirm Success");
        return ResponseEntity.ok(resp);
    }

    /**
     * Cập nhật trạng thái Bill theo kết quả xác thực — dùng chung cho Return URL và IPN.
     * <p>
     * Thất bại/hủy: đi qua BillService.updateStatus() để tái dùng đúng logic
     * hoàn kho + hoàn voucher đã có sẵn (giống job tự hủy đơn quá hạn), tránh
     * viết lại và có nguy cơ quên hoàn tồn kho đã trừ lúc đặt hàng.
     * Thành công: set thẳng CONFIRMED (không có gì cần hoàn) + tính lượt voucher,
     * giữ đúng hành vi cũ của luồng mock.
     * <p>
     * Khóa lại Bill bằng findByIdForUpdate ngay đầu hàm (bỏ qua instance được
     * verify() trả về, vốn không có lock): VNPay có thể gọi /vnpay-return
     * (qua trình duyệt khách) và /vnpay-ipn (server-to-server) gần như đồng
     * thời cho cùng 1 giao dịch, nên vẫn cần khóa + đọc lại trạng thái mới
     * nhất trước khi kiểm tra PENDING, giống hệt lý do PosOrderService.pay()
     * phải khóa Bill trước khi kiểm tra WAITING.
     */
    private void applyPaymentResult(Bill unlockedBill, VNPayService.VerifyResult result) {
        Bill bill = billRepository.findByIdForUpdate(unlockedBill.getId())
                .orElse(unlockedBill);

        if (!OrderStatus.PENDING.matches(bill.getStatus())) {
            return; // đã được xử lý trước đó (bởi Return URL hoặc IPN gọi lần khác) -> bỏ qua
        }

        boolean success = result.status() == VNPayService.VerifyStatus.SUCCESS;

        if (!success) {
            billService.updateStatus(bill.getId(), OrderStatus.CANCELLED.getValue(), "Thanh toán VNPay thất bại/bị hủy", null);
            return;
        }

        bill.setStatus(OrderStatus.CONFIRMED.getValue());
        billRepository.save(bill);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setBill(bill);
        history.setStatus(bill.getStatus());
        history.setNote("Đã thanh toán online qua VNPay (sandbox) - GD: " + result.transactionNo());
        history.setCreatedDate(LocalDateTime.now());
        orderStatusHistoryRepository.save(history);

        if (bill.getDiscountCode() != null) {
            discountCodeService.incrementUsage(bill.getDiscountCode().getId());
        }
    }
}