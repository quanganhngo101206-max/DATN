package com.skysport.datn.controller.customer;

import com.skysport.datn.dto.CheckoutRequest;
import com.skysport.datn.dto.DiscountApplyResult;
import com.skysport.datn.entity.*;
import com.skysport.datn.exception.BusinessException;
import com.skysport.datn.repository.*;
import com.skysport.datn.service.CheckoutService;
import com.skysport.datn.service.DiscountCodeService;
import com.skysport.datn.service.VNPayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class CheckoutController {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);

    private final CheckoutService checkoutService;
    private final VNPayService vnPayService;
    private final CustomerRepository customerRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final DiscountCodeService discountCodeService;
    private final DiscountCodeRepository discountCodeRepository;
    private final ProvinceRepository provinceRepository;
    private final BillRepository billRepository;
    private final BillDetailRepository billDetailRepository;
    private final AddressShippingRepository addressShippingRepository;

    /** Cổng thanh toán cho phương thức BANKING: "vnpay" (sandbox thật) hoặc "mock" (trang QR giả lập). */
    @Value("${payment.banking-provider:vnpay}")
    private String bankingProvider;

    private static final String CART_KEY = "cart";
    private static final double FREE_SHIP_THRESHOLD = 800000;

    private Map<Integer, CartController.CartItem> getCart(HttpSession session) {
        Map<Integer, CartController.CartItem> cart =
                (Map<Integer, CartController.CartItem>) session.getAttribute(CART_KEY);

        if (cart == null) {
            cart = new LinkedHashMap<>();
            session.setAttribute(CART_KEY, cart);
        }

        return cart;
    }

    // ===== Hiển thị trang thanh toán =====
    @GetMapping("/checkout")
    public String checkout(HttpSession session, Model model) {
        Map<Integer, CartController.CartItem> cart = getCart(session);

        if (cart.isEmpty()) {
            return "redirect:/cart";
        }

        double subtotal = cart.values().stream()
                .mapToDouble(i -> i.getPrice() * i.getQuantity())
                .sum();

        Account account = (Account) session.getAttribute("account");
        CheckoutRequest req = new CheckoutRequest();
        Customer customerForCheck = null;
        List<AddressShipping> addresses = new ArrayList<>();

        // Tự điền thông tin nếu đã đăng nhập
        if (account != null) {
            Customer customer = customerRepository.findByAccountId(account.getId());

            if (customer != null) {
                customerForCheck = customer;

                req.setFullName(customer.getName());
                req.setPhoneNumber(customer.getPhoneNumber());
                req.setEmail(customer.getEmail());

                // Lấy toàn bộ địa chỉ của khách hàng
                addresses = addressShippingRepository.findByCustomerIdOrderByIsDefaultDescIdAsc(customer.getId());

                // Ưu tiên địa chỉ mặc định
                AddressShipping defaultAddress = customer.getAddressShipping();

                // Nếu chưa có địa chỉ mặc định thì lấy địa chỉ đầu tiên
                if (defaultAddress == null && !addresses.isEmpty()) {
                    defaultAddress = addresses.get(0);
                }

                // Điền địa chỉ mặc định vào form checkout
                if (defaultAddress != null) {
                    req.setAddressId(defaultAddress.getId());
                    req.setAddress(defaultAddress.getAddress());
                    req.setFullName(defaultAddress.getReceiverName());
                    req.setPhoneNumber(defaultAddress.getReceiverPhone());
                    req.setProvinceId(defaultAddress.getProvinceId());
                    req.setWardId(defaultAddress.getWardId());
                }
            }
        }

        // Nếu đã có tỉnh trong địa chỉ lưu sẵn thì tính phí theo khu vực.
        // Khách mới chưa chọn tỉnh sẽ thấy yêu cầu chọn tỉnh trên giao diện.
        double shipping = checkoutService.calculateShipping(
                subtotal,
                req.getProvinceId()
        );

        double total = subtotal + shipping;

        // Danh sách mã giảm giá đang khả dụng với đơn hàng hiện tại
        LocalDateTime now = LocalDateTime.now();
        Integer customerId = customerForCheck != null
                ? customerForCheck.getId()
                : null;

        List<DiscountCode> availableDiscounts = discountCodeRepository
                .findByStatusAndDeleteFlagFalse(1)
                .stream()
                .filter(d -> d.getStartDate() == null
                        || !now.isBefore(d.getStartDate()))
                .filter(d -> d.getEndDate() == null
                        || !now.isAfter(d.getEndDate()))
                .filter(d -> d.getMaximumUsage() == null
                        || d.getUsedCount() == null
                        || d.getUsedCount() < d.getMaximumUsage())
                .filter(d -> d.getMinimumAmountInCart() == null
                        || subtotal >= d.getMinimumAmountInCart())
                .filter(d -> customerId == null
                        || !discountCodeService.hasCustomerUsedDiscount(
                        customerId,
                        d.getId()))
                .sorted((a, b) -> {
                    double da = discountCodeService.calculateDiscount(a, subtotal);
                    double db = discountCodeService.calculateDiscount(b, subtotal);

                    return Double.compare(db, da);
                })
                .collect(Collectors.toList());

        model.addAttribute("availableDiscounts", availableDiscounts);

        // Danh sách địa chỉ đã lưu
        model.addAttribute("addresses", addresses);

        model.addAttribute("checkoutRequest", req);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("shipping", shipping);
        model.addAttribute("total", total);
        model.addAttribute("cartItems", cart.values());
        model.addAttribute("cartCount", session.getAttribute("cartCount"));
        model.addAttribute("paymentMethods", paymentMethodRepository.findAll());
        model.addAttribute("freeShipThreshold", FREE_SHIP_THRESHOLD);

        // Danh sách tỉnh/thành
        model.addAttribute("provinces", provinceRepository.findAll());

        return "customer/checkout/index";
    }

    // ===== AJAX: Tính phí vận chuyển =====
    @PostMapping("/checkout/calculate-shipping")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> calculateShipping(
            @RequestParam double subtotal,
            @RequestParam(required = false) Integer provinceId) {
        double fee = checkoutService.calculateShipping(subtotal, provinceId);
        return ResponseEntity.ok(Map.of("shippingFee", fee));
    }

    // ===== AJAX: Validate mã giảm giá =====
    @PostMapping("/checkout/apply-discount")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> applyDiscount(
            @RequestParam String code,
            @RequestParam double subtotal,
            @RequestParam(required = false) Integer provinceId,
            HttpSession session) {

        Account account = (Account) session.getAttribute("account");
        Integer accountId = account != null
                ? account.getId()
                : null;

        DiscountApplyResult applyResult =
                checkoutService.applyDiscount(
                        code,
                        subtotal,
                        provinceId,
                        accountId
                );

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("success", applyResult.isSuccess());
        result.put("message", applyResult.getMessage());

        if (!applyResult.isSuccess()) {
            return ResponseEntity.ok(result);
        }

        // Chỉ lưu code vào session.
        // Backend sẽ tính lại amount khi checkout để tránh stale discount
        // nếu cart thay đổi sau khi áp mã.
        session.setAttribute("appliedDiscountCode", code);

        result.put("discountAmount", applyResult.getDiscountAmount());
        result.put("finalTotal", applyResult.getFinalTotal());
        result.put("shipping", applyResult.getShipping());

        return ResponseEntity.ok(result);
    }

    // ===== AJAX: Xóa mã giảm giá khỏi session =====
    @PostMapping("/checkout/remove-discount")
    @ResponseBody
    public ResponseEntity<Void> removeDiscount(HttpSession session) {
        session.removeAttribute("appliedDiscountCode");
        return ResponseEntity.ok().build();
    }

    // ===== Xử lý đặt hàng =====
    @PostMapping("/checkout")
    public String placeOrder(
            @Valid @ModelAttribute CheckoutRequest request,
            BindingResult bindingResult,
            HttpSession session,
            HttpServletRequest httpRequest,
            RedirectAttributes redirectAttributes) {

        Map<Integer, CartController.CartItem> cart = getCart(session);

        if (cart.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Giỏ hàng trống!"
            );

            return "redirect:/cart";
        }

        // Dùng @Valid thay cho kiểm tra thủ công
        if (bindingResult.hasErrors()) {
            String firstError =
                    bindingResult.getAllErrors()
                            .get(0)
                            .getDefaultMessage();

            redirectAttributes.addFlashAttribute(
                    "error",
                    firstError
            );

            return "redirect:/checkout";
        }

        try {
            Account account = (Account) session.getAttribute("account");

            Integer accountId = account != null
                    ? account.getId()
                    : null;

            String codeFromSession =
                    (String) session.getAttribute("appliedDiscountCode");

            Bill bill = checkoutService.placeOrder(
                    request,
                    cart,
                    accountId,
                    codeFromSession
            );

            // Lưu thông tin đơn vào session cho guest tra cứu
            session.setAttribute(
                    "lastOrderCode",
                    bill.getCode()
            );

            session.setAttribute(
                    "lastOrderId",
                    bill.getId()
            );

            session.setAttribute(
                    "lastOrderPhone",
                    request.getPhoneNumber()
            );

            // Xóa giỏ hàng và discount session
            session.removeAttribute(CART_KEY);
            session.removeAttribute("appliedDiscountCode");
            session.setAttribute("cartCount", 0);

            if ("BANKING".equals(request.getPaymentMethod())) {

                // Luồng chính: chuyển khách sang trang thanh toán VNPay sandbox.
                // Kết quả về qua /vnpay-return (trình duyệt) và /vnpay-ipn (server-to-server),
                // cùng đi vào BillService.processVnPayResult().
                if ("vnpay".equalsIgnoreCase(bankingProvider)) {
                    try {
                        VNPayService.PaymentUrlResult pay = vnPayService.createPaymentUrl(
                                bill,
                                Math.round(bill.getAmount()),
                                "Thanh toan don hang " + bill.getCode(),
                                httpRequest
                        );
                        return "redirect:" + pay.paymentUrl();
                    } catch (Exception e) {
                        // Đơn đã tạo (PENDING, đã trừ kho) — không để khách mất dấu đơn.
                        // Nếu không thanh toán, job auto-cancel sẽ hủy + hoàn kho sau hạn.
                        log.error("Không tạo được URL VNPay cho billId={}", bill.getId(), e);
                        redirectAttributes.addFlashAttribute(
                                "successMsg",
                                "Đơn " + bill.getCode() + " đã được tạo nhưng chưa tạo được liên kết "
                                        + "thanh toán VNPay. Vui lòng liên hệ shop hoặc đặt lại."
                        );
                        return "redirect:/order/success/" + bill.getId();
                    }
                }

                // Dự phòng (payment.banking-provider=mock): trang QR giả lập
                redirectAttributes.addAttribute(
                        "billId",
                        bill.getId()
                );

                redirectAttributes.addAttribute(
                        "amount",
                        Math.round(bill.getAmount())
                );

                return "redirect:/mock-vnpay";
            }

            redirectAttributes.addFlashAttribute(
                    "successMsg",
                    "Đặt hàng thành công! Mã đơn: "
                            + bill.getCode()
            );

            return "redirect:/order/success/" + bill.getId();

        } catch (BusinessException e) {
            session.removeAttribute("appliedDiscountCode");
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/checkout";
        } catch (Exception e) {
            log.error("Lỗi khi đặt hàng", e);
            session.removeAttribute("appliedDiscountCode");
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra, vui lòng thử lại!");
            return "redirect:/checkout";
        }
    }

    // ===== Trang đặt hàng thành công =====
    @GetMapping("/order/success/{billId}")
    public String orderSuccess(
            @PathVariable Integer billId,
            @ModelAttribute("successMsg") String successMsg,
            HttpSession session,
            Model model) {

        Bill bill = billRepository.findById(billId).orElse(null);

        if (bill == null) {
            return "redirect:/home";
        }

        Account account = (Account) session.getAttribute("account");

        Integer lastOrderId =
                (Integer) session.getAttribute("lastOrderId");

        boolean isOwner =
                (account != null
                        && bill.getCustomer() != null
                        && bill.getCustomer().getAccount() != null
                        && account.getId().equals(
                        bill.getCustomer()
                                .getAccount()
                                .getId()
                ))
                        || billId.equals(lastOrderId);

        if (!isOwner) {
            return "redirect:/home";
        }

        List<BillDetail> details =
                billDetailRepository.findByBillId(billId);

        model.addAttribute("bill", bill);
        model.addAttribute("details", details);
        model.addAttribute("successMsg", successMsg);

        return "customer/order/success";
    }
}