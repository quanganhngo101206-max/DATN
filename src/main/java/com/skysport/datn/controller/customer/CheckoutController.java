package com.skysport.datn.controller.customer;

import com.skysport.datn.dto.CheckoutRequest;
import com.skysport.datn.entity.*;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.repository.*;
import com.skysport.datn.service.CartService;
import com.skysport.datn.service.DiscountCodeService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class CheckoutController {

    @Autowired private CartService cartService;
    @Autowired private BillRepository billRepository;
    @Autowired private BillDetailRepository billDetailRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private DiscountCodeService discountCodeService;
    @Autowired private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired private ProductDetailRepository productDetailRepository;
    @Autowired private DiscountCodeRepository discountCodeRepository;

    @Autowired private ProvinceRepository provinceRepository;

    // ✅ THÊM MỚI: inject WardRepository để tra tên phường/xã
    @Autowired private WardRepository wardRepository;

    private static final String CART_KEY = "cart";
    private static final double FREE_SHIP_THRESHOLD = 500000;
    private static final double SHIPPING_FEE = 30000;

    private Map<Integer, CartController.CartItem> getCart(HttpSession session) {
        Map<Integer, CartController.CartItem> cart =
                (Map<Integer, CartController.CartItem>) session.getAttribute(CART_KEY);
        if (cart == null) { cart = new LinkedHashMap<>(); session.setAttribute(CART_KEY, cart); }
        return cart;
    }

    // ===== Hiển thị trang thanh toán =====
    @GetMapping("/checkout")
    public String checkout(HttpSession session, Model model) {
        Map<Integer, CartController.CartItem> cart = getCart(session);
        if (cart.isEmpty()) return "redirect:/cart";

        double subtotal = cart.values().stream()
                .mapToDouble(i -> i.getPrice() * i.getQuantity()).sum();
        double shipping = subtotal >= FREE_SHIP_THRESHOLD ? 0 : SHIPPING_FEE;
        double total = subtotal + shipping;

        // Tự điền thông tin nếu đã đăng nhập
        Account account = (Account) session.getAttribute("account");
        CheckoutRequest req = new CheckoutRequest();
        Customer customerForCheck = null;
        if (account != null) {
            Customer customer = customerRepository.findByAccountId(account.getId());
            if (customer != null) {
                customerForCheck = customer;
                req.setFullName(customer.getName());
                req.setPhoneNumber(customer.getPhoneNumber());
                req.setEmail(customer.getEmail());
                if (customer.getAddressShipping() != null) {
                    req.setAddress(customer.getAddressShipping().getAddress());
                    req.setProvinceId(customer.getAddressShipping().getProvinceId());
                    req.setWardId(customer.getAddressShipping().getWardId());
                }
            }
        }

        // Danh sách mã giảm giá đang khả dụng với đơn hàng hiện tại
        LocalDateTime now = LocalDateTime.now();
        Integer customerId = customerForCheck != null ? customerForCheck.getId() : null;
        List<DiscountCode> availableDiscounts = discountCodeRepository.findByStatusAndDeleteFlagFalse(1).stream()
                .filter(d -> d.getStartDate() == null || !now.isBefore(d.getStartDate()))
                .filter(d -> d.getEndDate() == null || !now.isAfter(d.getEndDate()))
                .filter(d -> d.getMaximumUsage() == null
                        || d.getUsedCount() == null
                        || d.getUsedCount() < d.getMaximumUsage())
                .filter(d -> d.getMinimumAmountInCart() == null || subtotal >= d.getMinimumAmountInCart())
                .filter(d -> customerId == null
                        || billRepository.countByCustomerIdAndDiscountCodeIdExcludingCancelled(customerId, d.getId()) == 0)
                .sorted((a, b) -> {
                    double da = discountCodeService.calculateDiscount(a, subtotal);
                    double db = discountCodeService.calculateDiscount(b, subtotal);
                    return Double.compare(db, da); // giảm nhiều nhất lên trước
                })
                .collect(Collectors.toList());
        model.addAttribute("availableDiscounts", availableDiscounts);

        model.addAttribute("checkoutRequest", req);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("shipping", shipping);
        model.addAttribute("total", total);
        model.addAttribute("cartItems", cart.values());
        model.addAttribute("cartCount", session.getAttribute("cartCount"));
        model.addAttribute("paymentMethods", paymentMethodRepository.findAll());
        model.addAttribute("freeShipThreshold", FREE_SHIP_THRESHOLD);

        // Truyền danh sách tỉnh/thành vào model để render dropdown
        model.addAttribute("provinces", provinceRepository.findAll());

        return "customer/checkout/index";
    }

    // ===== AJAX: Validate mã giảm giá =====
    @PostMapping("/checkout/apply-discount")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> applyDiscount(
            @RequestParam String code,
            @RequestParam double subtotal,
            HttpSession session) {

        Map<String, Object> result = new LinkedHashMap<>();
        double shipping = subtotal >= FREE_SHIP_THRESHOLD ? 0 : SHIPPING_FEE;
        double total = subtotal + shipping;

        Integer customerId = null;
        Account account = (Account) session.getAttribute("account");
        if (account != null) {
            Customer customer = customerRepository.findByAccountId(account.getId());
            if (customer != null) customerId = customer.getId();
        }

        String validation = discountCodeService.validate(code, subtotal, customerId);
        if (!"OK".equals(validation)) {
            result.put("success", false);
            result.put("message", validation);
            return ResponseEntity.ok(result);
        }

        DiscountCode discount = discountCodeService.findByCode(code);
        double discountAmount = discountCodeService.calculateDiscount(discount, subtotal);
        if (discountAmount > total) discountAmount = total;

        double finalTotal = total - discountAmount;

        // Lưu vào session để dùng lúc đặt hàng
        session.setAttribute("appliedDiscountCode", code);
        session.setAttribute("appliedDiscountAmount", discountAmount);

        result.put("success", true);
        result.put("message", "Áp dụng thành công!");
        result.put("discountAmount", (long) discountAmount);
        result.put("finalTotal", (long) finalTotal);
        result.put("shipping", (long) shipping);
        return ResponseEntity.ok(result);
    }

    // ===== Xử lý đặt hàng =====
    @Transactional
    @PostMapping("/checkout")
    public String placeOrder(@Valid @ModelAttribute CheckoutRequest request,
                             BindingResult bindingResult,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {

        Map<Integer, CartController.CartItem> cart = getCart(session);
        if (cart.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Giỏ hàng trống!");
            return "redirect:/cart";
        }

        // Dùng @Valid thay cho kiểm tra thủ công
        if (bindingResult.hasErrors()) {
            String firstError = bindingResult.getAllErrors().get(0).getDefaultMessage();
            redirectAttributes.addFlashAttribute("error", firstError);
            return "redirect:/checkout";
        }

        try {
            // Tính tiền
            double subtotal = cart.values().stream()
                    .mapToDouble(i -> i.getPrice() * i.getQuantity()).sum();
            double shipping = subtotal >= FREE_SHIP_THRESHOLD ? 0 : SHIPPING_FEE;
            double total = subtotal + shipping;

            // Khách hàng — xác định trước để dùng cho validate mã giảm giá
            Account account = (Account) session.getAttribute("account");
            Customer customer = null;
            if (account != null) {
                customer = customerRepository.findByAccountId(account.getId());
            }
            if (customer == null) {
                // Tìm theo SĐT trước — tránh tạo trùng Customer cho guest
                customer = customerRepository.findByPhoneNumber(request.getPhoneNumber()).orElse(null);
            }
            if (customer == null) {
                customer = new Customer();
                customer.setName(request.getFullName());
                customer.setPhoneNumber(request.getPhoneNumber());
                customer.setEmail(request.getEmail() != null && !request.getEmail().isBlank()
                        ? request.getEmail() : null);
                customer.setCode(generateCustomerCode());
                customer = customerRepository.save(customer);
            }

            // Kiểm tra mã giảm giá từ session (đã validate qua AJAX) hoặc từ form
            double discountAmount = 0;
            DiscountCode appliedDiscount = null;

            String codeFromSession = (String) session.getAttribute("appliedDiscountCode");
            Double discountFromSession = (Double) session.getAttribute("appliedDiscountAmount");

            String codeToUse = codeFromSession != null ? codeFromSession : request.getDiscountCode();

            if (codeToUse != null && !codeToUse.isBlank()) {
                String validation = discountCodeService.validate(codeToUse, subtotal, customer.getId());
                if ("OK".equals(validation)) {
                    appliedDiscount = discountCodeService.findByCode(codeToUse);
                    discountAmount = discountFromSession != null
                            ? discountFromSession
                            : discountCodeService.calculateDiscount(appliedDiscount, subtotal);
                    if (discountAmount > total) discountAmount = total;
                }
            }

            double finalAmount = total - discountAmount;

            // ✅ SỬA LỖI: Ghép địa chỉ đầy đủ = số nhà + phường/xã + tỉnh/thành
            String fullAddress = request.getAddress();
            if (request.getWardId() != null) {
                Ward ward = wardRepository.findById(request.getWardId()).orElse(null);
                if (ward != null) {
                    fullAddress += ", " + ward.getName();
                }
            }
            if (request.getProvinceId() != null) {
                Province province = provinceRepository.findById(request.getProvinceId()).orElse(null);
                if (province != null) {
                    fullAddress += ", " + province.getName();
                }
            }

            // Tạo Bill
            Bill bill = new Bill();
            bill.setCreateDate(LocalDateTime.now());
            bill.setUpdateDate(LocalDateTime.now());
            bill.setStatus(OrderStatus.PENDING.getValue());
            // ✅ Dùng fullAddress thay vì request.getAddress()
            bill.setBillingAddress(fullAddress);
            bill.setInvoiceType(1);
            bill.setAmount((float) finalAmount);
            bill.setSubtotal((float) subtotal);
            bill.setShippingFee((float) shipping);
            bill.setNote(request.getNote());

            if (appliedDiscount != null) {
                bill.setDiscountCode(appliedDiscount);
                bill.setPromotionPrice((float) discountAmount);
            }

            bill.setCustomer(customer);

            // Phương thức thanh toán
            String requestedMethod = request.getPaymentMethod();
            List<Payment> methods = paymentMethodRepository.findAll();
            Payment paymentMethod = methods.stream()
                    .filter(m -> {
                        if (m.getName() == null) return false;
                        String upper = m.getName().toUpperCase();
                        if ("BANKING".equals(requestedMethod)) {
                            return upper.contains("CHUY") || upper.contains("BANK") || upper.contains("KHO");
                        }
                        return upper.contains("TIỀN") || upper.contains("MẶT") || upper.contains("COD");
                    })
                    .findFirst()
                    .orElse(methods.isEmpty() ? null : methods.get(0));
            bill.setPaymentMethod(paymentMethod);

            // Khóa từng dòng tồn kho (PESSIMISTIC_WRITE)
            Map<Integer, ProductDetail> lockedDetails = new LinkedHashMap<>();
            for (CartController.CartItem item : cart.values()) {
                ProductDetail pd = productDetailRepository.findByIdForUpdate(item.getProductDetailId())
                        .orElse(null);
                if (pd == null) {
                    throw new RuntimeException("Sản phẩm không còn tồn tại trong hệ thống!");
                }
                var prod = pd.getProduct();
                if (prod == null || Boolean.TRUE.equals(prod.getDeleteFlag())
                        || prod.getStatus() == null || prod.getStatus() != 1) {
                    throw new RuntimeException("Sản phẩm \"" + item.getProductName()
                            + "\" hiện không còn được bán!");
                }
                if (pd.getQuantity() == null || pd.getQuantity() < item.getQuantity()) {
                    throw new RuntimeException("Sản phẩm \"" + item.getProductName()
                            + "\" chỉ còn " + (pd.getQuantity() != null ? pd.getQuantity() : 0)
                            + " sản phẩm trong kho!");
                }
                lockedDetails.put(item.getProductDetailId(), pd);
            }

            bill = billRepository.save(bill);
            bill.setCode("HD" + String.format("%05d", bill.getId()));
            bill = billRepository.save(bill);

            // Lưu thông tin đơn vào session (cho guest tra cứu)
            session.setAttribute("lastOrderCode", bill.getCode());
            session.setAttribute("lastOrderId", bill.getId());
            session.setAttribute("lastOrderPhone", request.getPhoneNumber());

            // Tạo BillDetail + trừ tồn kho
            for (CartController.CartItem item : cart.values()) {
                ProductDetail productDetail = lockedDetails.get(item.getProductDetailId());

                BillDetail detail = new BillDetail();
                detail.setBill(bill);
                detail.setProductDetail(productDetail);
                detail.setMomentPrice(item.getPrice().floatValue());
                detail.setQuantity(item.getQuantity());
                billDetailRepository.save(detail);

                productDetail.setQuantity(productDetail.getQuantity() - item.getQuantity());
                cartService.updateProductDetail(productDetail);
            }

            // Ghi lịch sử
            OrderStatusHistory history = new OrderStatusHistory();
            history.setBill(bill);
            history.setStatus(1);
            history.setNote("Đơn hàng được tạo - " + request.getFullName());
            history.setCreatedDate(LocalDateTime.now());
            orderStatusHistoryRepository.save(history);

            // Trừ lượt dùng mã giảm giá
            if (appliedDiscount != null) {
                discountCodeService.decreaseUsage(appliedDiscount.getId());
            }

            // Xóa giỏ hàng và discount session
            session.removeAttribute(CART_KEY);
            session.removeAttribute("appliedDiscountCode");
            session.removeAttribute("appliedDiscountAmount");
            session.setAttribute("cartCount", 0);

            redirectAttributes.addFlashAttribute("successMsg", "Đặt hàng thành công! Mã đơn: " + bill.getCode());
            return "redirect:/order/success/" + bill.getId();

        } catch (Exception e) {
            e.printStackTrace();
            session.removeAttribute("appliedDiscountCode");
            session.removeAttribute("appliedDiscountAmount");
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra: " + e.getMessage());
            return "redirect:/checkout";
        }
    }

    // ===== Trang đặt hàng thành công =====
    @GetMapping("/order/success/{billId}")
    public String orderSuccess(@PathVariable Integer billId,
                               @ModelAttribute("successMsg") String successMsg,
                               HttpSession session,
                               Model model) {
        Bill bill = billRepository.findById(billId).orElse(null);
        if (bill == null) return "redirect:/home";

        Account account = (Account) session.getAttribute("account");
        Integer lastOrderId = (Integer) session.getAttribute("lastOrderId");
        boolean isOwner = (account != null && bill.getCustomer() != null
                && bill.getCustomer().getAccount() != null
                && account.getId().equals(bill.getCustomer().getAccount().getId()))
                || (billId.equals(lastOrderId));
        if (!isOwner) return "redirect:/home";

        List<BillDetail> details = billDetailRepository.findByBillId(billId);
        model.addAttribute("bill", bill);
        model.addAttribute("details", details);
        model.addAttribute("successMsg", successMsg);
        return "customer/order/success";
    }

    private String generateBillCode() {
        Integer maxId = billRepository.findMaxId();
        int next = (maxId == null ? 0 : maxId) + 1;
        return "HD" + String.format("%03d", next);
    }

    private String generateCustomerCode() {
        Integer maxId = customerRepository.findMaxId();
        int next = (maxId == null ? 0 : maxId) + 1;
        return "KH" + String.format("%03d", next);
    }
}
