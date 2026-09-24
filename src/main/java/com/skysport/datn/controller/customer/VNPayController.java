package com.skysport.datn.controller.customer;

import com.skysport.datn.entity.Bill;
import com.skysport.datn.repository.BillRepository;
import com.skysport.datn.service.BillService;
import com.skysport.datn.service.VNPayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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
    private final BillService billService;
    private final BillRepository billRepository;

    /*
     * KHÔNG đánh @Transactional ở controller: toàn bộ việc lock Bill + đổi trạng thái +
     * hoàn kho + voucher nằm trong BillService.processVnPayResult() — 1 transaction duy nhất.
     */
    @GetMapping("/vnpay-return")
    public String vnpayReturn(HttpServletRequest request, Model model) {
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
        boolean success = result.status() == VNPayService.VerifyStatus.SUCCESS;
        // Return trùng IPN / F5: processVnPayResult trả false (đã xử lý) -> bỏ qua, vẫn hiển thị kết quả
        billService.processVnPayResult(bill.getId(), success, result.transactionNo());

// Reload Bill để lấy trạng thái mới nhất sau khi process
        Bill updatedBill = billRepository.findById(bill.getId()).orElse(bill);
        model.addAttribute("bill", updatedBill);
        model.addAttribute("success", success);
        model.addAttribute("transactionNo", result.transactionNo());
        model.addAttribute("payDate", result.payDate());
        return "customer/vnpay/vnpay-result";
    }

    /**
     * IPN: PHẢI trả về đúng format JSON VNPay quy định, không phải trang HTML.
     * Mã RspCode chuẩn: "00" = Confirm Success, "01" = Order not found,
     * "02" = Order already confirmed, "04" = Invalid amount, "97" = Invalid signature.
     */
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

        boolean success = result.status() == VNPayService.VerifyStatus.SUCCESS;
        // Idempotency nằm trong service (kiểm tra PENDING sau khi lock Bill)
        boolean processed = billService.processVnPayResult(result.bill().getId(), success, result.transactionNo());

        if (!processed) {
            resp.put("RspCode", "02");
            resp.put("Message", "Order already confirmed");
            return ResponseEntity.ok(resp);
        }

        resp.put("RspCode", "00");
        resp.put("Message", "Confirm Success");
        return ResponseEntity.ok(resp);
    }
}