package com.skysport.datn.controller.customer;

import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.Bill;
import com.skysport.datn.entity.BillDetail;
import com.skysport.datn.entity.OrderStatusHistory;
import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.repository.BillDetailRepository;
import com.skysport.datn.repository.BillRepository;
import com.skysport.datn.repository.OrderStatusHistoryRepository;
import com.skysport.datn.service.CartService;
import com.skysport.datn.service.DiscountCodeService;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import com.skysport.datn.exception.BusinessException;

@Controller
@RequiredArgsConstructor
public class MockVNPayController {

    private static final String CART_SESSION_KEY = "cart";

    /**
     * +     * Thời hạn QR = thời gian đếm ngược ở mock-vnpay.html. Tính từ
     * +     * Bill.createDate vì token luôn được sinh trong cùng lượt redirect
     * +     * ngay sau khi tạo đơn (xem CheckoutController), nên createDate ~
     * +     * thời điểm QR được tạo, không cần thêm cột mới. Dùng chung property
     * +     * app.qr-payment.expire-minutes với BillService.pendingBankingTimeoutMinutes
     * +     * để không bao giờ lệch giữa UI và lúc job thực sự hủy đơn.
     */
    @Value("${app.qr-payment.expire-minutes:15}")
    private long qrExpireMinutes;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private final BillRepository billRepository;

    private final BillDetailRepository billDetailRepository;

    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    private final DiscountCodeService discountCodeService;

    private final CartService cartService;

    /**
     * Kiểm tra người đang thao tác có đúng là chủ đơn hàng không.
     */
    private boolean isOwner(Bill bill, HttpSession session) {

        if (bill == null) {
            return false;
        }

        Account account =
                (Account) session.getAttribute("account");

        boolean ownedByAccount =
                account != null
                        && bill.getCustomer() != null
                        && bill.getCustomer().getAccount() != null
                        && account.getId().equals(
                        bill.getCustomer()
                                .getAccount()
                                .getId()
                );

        Integer lastOrderId =
                (Integer) session.getAttribute("lastOrderId");

        boolean ownedByGuestSession =
                lastOrderId != null
                        && bill.getId().equals(lastOrderId);

        return ownedByAccount || ownedByGuestSession;
    }

    /**
     * QR hết hạn khi quá QR_EXPIRE_MINUTES kể từ lúc đơn được tạo.
     * Đơn đã CONFIRMED thì không tính hết hạn (đã thanh toán xong rồi).
     */
    private boolean isExpired(Bill bill) {

        if (OrderStatus.CONFIRMED.matches(bill.getStatus())) {
            return false;
        }

        if (bill.getCreateDate() == null) {
            return false;
        }

        return bill.getCreateDate()
                .plusMinutes(qrExpireMinutes)
                .isBefore(LocalDateTime.now());
    }

    /**
     * Nạp lại giỏ hàng từ chính đơn vừa hết hạn, để khách bấm "Quay lại
     * giỏ hàng" thấy đúng sản phẩm cũ thay vì giỏ trống — CheckoutController
     * đã xóa giỏ ngay khi tạo đơn, nên nếu không nạp lại thì nút này vô dụng.
     * Không kiểm tra tồn kho ở đây: việc đó đã có sẵn ở bước /cart/update
     * và lúc đặt hàng lại (processOrder), đủ để chặn trường hợp hết hàng.
     */
    private void restoreCartFromBill(Bill bill, HttpSession session) {
        List<BillDetail> billDetails = billDetailRepository.findByBillId(bill.getId());

        Map<Integer, CartController.CartItem> cart = new LinkedHashMap<>();

        for (BillDetail bd : billDetails) {
            ProductDetail pd = bd.getProductDetail();
            if (pd == null || pd.getProduct() == null) continue;

            CartController.CartItem item = new CartController.CartItem();
            item.setProductDetailId(pd.getId());
            item.setProductId(pd.getProduct().getId());
            item.setProductName(pd.getProduct().getName());
            item.setPrice(bd.getMomentPrice() != null ? bd.getMomentPrice().doubleValue() : 0.0);
            item.setQuantity(bd.getQuantity() != null ? bd.getQuantity() : 1);
            item.setColor(pd.getColor() != null ? pd.getColor().getName() : null);
            item.setSize(pd.getSize() != null ? pd.getSize().getName() : null);
            item.setImageUrl(cartService.getProductImage(pd.getProduct().getId()));

            cart.put(pd.getId(), item);
        }

        session.setAttribute(CART_SESSION_KEY, cart);

        int totalQty = cart.values().stream()
                .mapToInt(CartController.CartItem::getQuantity)
                .sum();
        session.setAttribute("cartCount", totalQty);
    }

    /**
     * Trang thanh toán QR Mock.
     */
    @GetMapping("/mock-vnpay")
    public String showMockVNPay(
            @RequestParam("billId") Integer billId,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        Bill bill =
                billRepository.findById(billId)
                        .orElse(null);

        if (bill == null) {
            return "redirect:/home";
        }

        if (!isOwner(bill, session)) {
            return "redirect:/home";
        }

        // Đơn đã hết hạn (quá 15 phút, chưa thanh toán) -> không hiện
        // lại QR với đồng hồ mới, đá thẳng về giỏ hàng như khi đồng hồ
        // JS tự đếm về 0 ngay trên trang. Nạp lại giỏ hàng trước để nút
        // "Quay lại giỏ hàng" ở đó thực sự có sản phẩm để đặt lại.
        if (isExpired(bill)) {
            restoreCartFromBill(bill, session);
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    "Giao dịch đã hết hạn, vui lòng đặt lại đơn hàng!"
            );
            return "redirect:/cart";
        }

        /*
         * Chỉ tạo token một lần cho mỗi đơn hàng.
         */
        if (bill.getVnpTxnRef() == null
                || bill.getVnpTxnRef().isBlank()) {

            bill.setVnpTxnRef(
                    "MOCK-" + UUID.randomUUID()
            );

            billRepository.save(bill);
        }

        /*
         * URL mà điện thoại sẽ truy cập
         * sau khi quét QR.
         */
        String qrUrl =
                baseUrl
                        + "/mock-vnpay/qr-pay"
                        + "?billId="
                        + bill.getId()
                        + "&token="
                        + URLEncoder.encode(
                        bill.getVnpTxnRef(),
                        StandardCharsets.UTF_8
                );

        /*
         * Tạo URL ảnh QR.
         *
         * qrUrl phải được encode vì bản thân qrUrl
         * chứa ?, &, =.
         */
        String qrImageUrl =
                "https://quickchart.io/qr?text="
                        + URLEncoder.encode(
                        qrUrl,
                        StandardCharsets.UTF_8
                )
                        + "&size=280";

        model.addAttribute("bill", bill);

        /*
         * Lấy số tiền trực tiếp từ Bill,
         * không lấy amount từ URL nữa.
         */
        model.addAttribute(
                "amount",
                Math.round(bill.getAmount())
        );

        model.addAttribute("qrUrl", qrUrl);
        model.addAttribute("qrImageUrl", qrImageUrl);
        model.addAttribute("expireMinutes", qrExpireMinutes);
        return "customer/vnpay/mock-vnpay";
    }

    /**
     * Điện thoại quét QR -> chỉ hiển thị màn hình xác nhận.
     * Chưa cập nhật trạng thái đơn ở bước này.
     */
    @GetMapping("/mock-vnpay/qr-pay")
    public String qrPayment(
            @RequestParam("billId") Integer billId,
            @RequestParam("token") String token,
            Model model) {

        Bill bill = billRepository.findById(billId).orElse(null);

        if (bill == null) {
            return "redirect:/home";
        }

        // QR của đơn nào chỉ được dùng với đúng token của đơn đó.
        if (bill.getVnpTxnRef() == null
                || bill.getVnpTxnRef().isBlank()
                || token == null
                || !bill.getVnpTxnRef().equals(token)) {
            return "redirect:/home";
        }

        // Nếu đã thanh toán, không cho xác nhận lại.
        if (OrderStatus.CONFIRMED.matches(bill.getStatus())) {
            model.addAttribute("bill", bill);
            model.addAttribute("alreadyPaid", true);
            return "customer/vnpay/mock-vnpay-confirm";
        }

        // Chỉ cho xác nhận đơn đang chờ thanh toán.
        if (!OrderStatus.PENDING.matches(bill.getStatus())) {
            return "redirect:/home";
        }

        // QR quá hạn thì không cho xác nhận nữa.
        if (isExpired(bill)) {
            model.addAttribute("bill", bill);
            model.addAttribute("alreadyPaid", false);
            model.addAttribute("expired", true);
            return "customer/vnpay/mock-vnpay-confirm";
        }

        model.addAttribute("bill", bill);
        model.addAttribute("token", token);
        model.addAttribute("alreadyPaid", false);

        return "customer/vnpay/mock-vnpay-confirm";
    }

    /**
     * Điện thoại bấm Xác nhận -> mới thực sự hoàn tất giao dịch.
     */
    @Transactional
    @PostMapping("/mock-vnpay/confirm")
    public String confirmPayment(
            @RequestParam("billId") Integer billId,
            @RequestParam("token") String token,
            RedirectAttributes redirectAttributes) {

        // Khóa PESSIMISTIC_WRITE: điện thoại có thể bấm "Xác nhận" 2 lần liên
        // tiếp (double-tap / mất mạng bấm lại), cùng pattern race với POS pay().
        Bill bill = billRepository.findByIdForUpdate(billId).orElse(null);

        if (bill == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Không tìm thấy đơn hàng!");
            return "redirect:/home";
        }

        if (bill.getVnpTxnRef() == null
                || bill.getVnpTxnRef().isBlank()
                || token == null
                || !bill.getVnpTxnRef().equals(token)) {
            redirectAttributes.addFlashAttribute("errorMsg", "Mã QR không hợp lệ hoặc đã hết hiệu lực!");
            return "redirect:/home";
        }

        // Nếu đã thanh toán thì chỉ hiển thị lại kết quả, không tăng voucher lần nữa.
        if (OrderStatus.CONFIRMED.matches(bill.getStatus())) {
            redirectAttributes.addAttribute("billId", bill.getId());
            redirectAttributes.addAttribute("transactionNo", "QR-DA-THANH-TOAN");
            redirectAttributes.addAttribute("payDate", bill.getUpdateDate() != null
                    ? bill.getUpdateDate().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"))
                    : LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy")));
            redirectAttributes.addAttribute("token", token);
            return "redirect:/mock-vnpay-success";
        }

        if (!OrderStatus.PENDING.matches(bill.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMsg", "Đơn hàng không thể thanh toán!");
            return "redirect:/home";
        }

        // QR quá hạn thì không cho xác nhận nữa (chặn lại lần cuối
        // phòng trường hợp người dùng vẫn còn mở trang confirm cũ).
        if (isExpired(bill)) {
            redirectAttributes.addFlashAttribute("errorMsg", "Mã QR đã hết hạn, vui lòng đặt lại đơn hàng!");
            return "redirect:/home";
        }

        // ==============================
        // HOÀN TẤT THANH TOÁN
        // ==============================
        LocalDateTime paidAt = LocalDateTime.now();

        bill.setStatus(OrderStatus.CONFIRMED.getValue());
        billRepository.save(bill);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setBill(bill);
        history.setStatus(OrderStatus.CONFIRMED.getValue());
        history.setNote("Đã thanh toán online bằng QR (Mock)");
        history.setCreatedDate(paidAt);
        orderStatusHistoryRepository.save(history);

        if (bill.getDiscountCode() != null) {
            discountCodeService.incrementUsage(bill.getDiscountCode().getId());
        }

        String transactionNo = "QR" + System.currentTimeMillis();
        String payDate = paidAt.format(
                DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy")
        );

        redirectAttributes.addAttribute("billId", bill.getId());
        redirectAttributes.addAttribute("transactionNo", transactionNo);
        redirectAttributes.addAttribute("payDate", payDate);
        redirectAttributes.addAttribute("token", token);

        return "redirect:/mock-vnpay-success";
    }

    /**
     * Laptop polling trạng thái thanh toán.
     * Có token để không cho người ngoài dò trạng thái đơn bằng billId.
     */
    @GetMapping("/mock-vnpay/check-status")
    @ResponseBody
    public Map<String, Object> checkStatus(
            @RequestParam Integer billId,
            @RequestParam String token) {

        Bill bill = billRepository.findById(billId)
                .orElseThrow(() ->
                        new BusinessException("Không tìm thấy hóa đơn"));

        Map<String, Object> response = new HashMap<>();

        boolean tokenValid =
                bill.getVnpTxnRef() != null
                        && bill.getVnpTxnRef().equals(token);

        boolean paid =
                tokenValid
                        && OrderStatus.CONFIRMED.matches(bill.getStatus());

        response.put("paid", paid);

        return response;
    }

    /**
     * Trang thanh toán thành công.
     */
    @GetMapping("/mock-vnpay-success")
    public String showMockVNPaySuccess(
            @RequestParam("billId") Integer billId,
            @RequestParam("transactionNo") String transactionNo,
            @RequestParam("payDate") String payDate,
            @RequestParam("token") String token,
            Model model) {

        Bill bill = billRepository.findById(billId).orElse(null);

        if (bill == null) {
            return "redirect:/home";
        }

        if (bill.getVnpTxnRef() == null
                || token == null
                || !bill.getVnpTxnRef().equals(token)) {
            return "redirect:/home";
        }

        if (!OrderStatus.CONFIRMED.matches(bill.getStatus())) {
            return "redirect:/home";
        }

        model.addAttribute("bill", bill);
        model.addAttribute("transactionNo", transactionNo);
        model.addAttribute("payDate", payDate);

        return "customer/vnpay/mock-vnpay-success";
    }
}