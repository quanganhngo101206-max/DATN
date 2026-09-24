package com.skysport.datn.controller;

import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.Bill;
import com.skysport.datn.entity.BillDetail;
import com.skysport.datn.entity.Customer;
import com.skysport.datn.entity.DiscountCode;
import com.skysport.datn.entity.Image;
import com.skysport.datn.entity.Product;
import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.entity.Staff;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.enums.PosOrderStatus;
import com.skysport.datn.enums.RoleName;
import com.skysport.datn.repository.BillDetailRepository;
import com.skysport.datn.repository.CustomerRepository;
import com.skysport.datn.repository.ImageRepository;
import com.skysport.datn.repository.PaymentMethodRepository;
import com.skysport.datn.repository.ProductDetailRepository;
import com.skysport.datn.repository.ProductRepository;
import com.skysport.datn.repository.StaffRepository;
import com.skysport.datn.service.DiscountCodeService;
import com.skysport.datn.service.PosOrderService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import com.skysport.datn.exception.BusinessException;

@Controller
@RequestMapping("/pos")
@RequiredArgsConstructor
public class PosOrderController {

    private final BillDetailRepository billDetailRepository;

    private final CustomerRepository customerRepository;

    private final ProductDetailRepository productDetailRepository;

    private final DiscountCodeService discountCodeService;

    private final PosOrderService posOrderService;

    private final StaffRepository staffRepository;

    private final ProductRepository productRepository;

    private final PaymentMethodRepository paymentMethodRepository;

    private final ImageRepository imageRepository;

    // ===== POS được cả Admin lẫn Nhân viên truy cập (xem SecurityConfig:
    // /pos/** cho phép hasAnyRole("ADMIN","STAFF")), nhưng trước đây mọi trang
    // POS đều cứng dùng layout của staff (staff/fragments/layout), nên Admin
    // bấm "Bán tại quầy" từ sidebar admin lại bị "rơi" sang giao diện nhân
    // viên. Hàm này xác định đúng vai trò hiện tại để mỗi trang POS tự chọn
    // layout tương ứng (admin/fragments/layout hay staff/fragments/layout).
    private boolean isAdmin(HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        return account != null && account.getRole() != null
                && RoleName.ADMIN.matches(account.getRole().getName());
    }

    // ===== Trang POS =====
    @GetMapping("/create")
    public String createForm(HttpSession session, Model model) {
        Account account = (Account) session.getAttribute("account");
        model.addAttribute("isAdmin", isAdmin(session));

        if (account != null) {
            Staff staff = staffRepository.findByAccountId(account.getId());
            model.addAttribute("staff", staff);
        }

        // Chỉ ẩn sản phẩm đã tạm dừng/xóa mềm ở chính nó (product.status != 1).
        // KHÔNG lọc theo isMasterDataActive() (category/brand/material tạm dừng)
        // nữa — cùng logic đã áp dụng ở CustomerProductController/HomeController:
        // hàng tồn vẫn phải bán hết được kể cả khi category/brand/material của nó
        // đang bị tạm dừng. Trước đây POS lọc thêm điều kiện này nên bị lệch với
        // trang khách hàng: sản phẩm khách vẫn mua được trên web lại biến mất khỏi
        // màn hình bán tại quầy.
        List<Product> products = productRepository.findByDeleteFlagFalse().stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .toList();

        Map<Integer, String> productImages = new HashMap<>();

        for (Product p : products) {
            List<Image> imgs = imageRepository.findByProductId(p.getId());

            if (!imgs.isEmpty()) {
                productImages.put(p.getId(), imgs.get(0).getLink());
            }
        }

        model.addAttribute("products", products);
        model.addAttribute("productImages", productImages);
        model.addAttribute("paymentMethods", paymentMethodRepository.findAll());
        model.addAttribute("waitingOrders", posOrderService.findWaitingOrders());

        return "pos/create";
    }

    // ===== AJAX: Tìm sản phẩm =====
    @GetMapping("/search-products")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchProducts(@RequestParam String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();

        // Cùng lý do như /create — không lọc theo isMasterDataActive() nữa,
        // chỉ ẩn khi chính sản phẩm bị tạm dừng.
        List<Product> allActive = productRepository.findByDeleteFlagFalse().stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .filter(p -> kw.isEmpty()
                        || (p.getName() != null && p.getName().toLowerCase().contains(kw))
                        || (p.getCode() != null && p.getCode().toLowerCase().contains(kw)))
                .toList();

        Set<Integer> bestSellerIds = new LinkedHashSet<>();

        for (Object[] row : billDetailRepository.findBestSellingProductIds()) {
            bestSellerIds.add((Integer) row[0]);

            if (bestSellerIds.size() >= 8) {
                break;
            }
        }

        Set<Integer> onSaleIds = new LinkedHashSet<>(
                productDetailRepository.findProductIdsOnSale(LocalDateTime.now())
        );

        List<Map<String, Object>> result = new ArrayList<>();

        for (Product p : allActive) {
            Map<String, Object> m = toProductDto(p);
            m.put("isBestSeller", bestSellerIds.contains(p.getId()));
            m.put("isOnSale", onSaleIds.contains(p.getId()));

            boolean isNew = p.getCreateDate() != null
                    && p.getCreateDate().isAfter(LocalDateTime.now().minusDays(30));

            m.put("isNew", isNew);
            result.add(m);

            if (result.size() >= 30) {
                break;
            }
        }

        return ResponseEntity.ok(result);
    }

    private Map<String, Object> toProductDto(Product p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("code", p.getCode() != null ? p.getCode() : "");
        m.put("name", p.getName());

        List<Image> imgs = imageRepository.findByProductId(p.getId());
        m.put("image", !imgs.isEmpty() ? imgs.get(0).getLink() : "/images/no-image.png");

        return m;
    }

    // ===== AJAX: Tìm khách hàng =====
    @GetMapping("/search-customer")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchCustomer(@RequestParam String keyword) {
        List<Map<String, Object>> result = new ArrayList<>();

        String kw = keyword == null ? "" : keyword.trim();

        if (kw.isEmpty()) {
            return ResponseEntity.ok(result);
        }

        List<Customer> customers = customerRepository.searchByKeyword(kw);

        for (Customer c : customers) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("code", c.getCode() != null ? c.getCode() : "");
            m.put("name", c.getName() != null ? c.getName() : "");
            m.put("phone", c.getPhoneNumber() != null ? c.getPhoneNumber() : "");
            m.put("email", c.getEmail() != null ? c.getEmail() : "");

            result.add(m);

            if (result.size() >= 10) {
                break;
            }
        }

        return ResponseEntity.ok(result);
    }

    // ===== AJAX: Gợi ý sản phẩm =====
    @GetMapping("/suggested-products")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> suggestedProducts() {
        Map<String, Object> result = new HashMap<>();

        // Cùng lý do như /create và /search-products.
        List<Product> allActive = productRepository.findByDeleteFlagFalse().stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .toList();

        Map<Integer, Product> productMap = new HashMap<>();

        for (Product p : allActive) {
            productMap.put(p.getId(), p);
        }

        // ===== Sản phẩm bán chạy =====
        List<Map<String, Object>> bestSellers = new ArrayList<>();

        for (Object[] row : billDetailRepository.findBestSellingProductIds()) {
            Integer productId = (Integer) row[0];
            Product p = productMap.get(productId);

            if (p == null) {
                continue;
            }

            bestSellers.add(toProductDto(p));

            if (bestSellers.size() >= 8) {
                break;
            }
        }

        // ===== Sản phẩm đang sale =====
        List<Map<String, Object>> onSale = new ArrayList<>();

        List<Integer> saleIds = productDetailRepository.findProductIdsOnSale(LocalDateTime.now());

        for (Integer productId : saleIds) {
            Product p = productMap.get(productId);

            if (p == null) {
                continue;
            }

            onSale.add(toProductDto(p));

            if (onSale.size() >= 8) {
                break;
            }
        }

        // ===== Sản phẩm mới =====
        List<Map<String, Object>> newProducts = allActive.stream()
                .sorted((a, b) -> {
                    if (a.getCreateDate() == null) {
                        return 1;
                    }

                    if (b.getCreateDate() == null) {
                        return -1;
                    }

                    return b.getCreateDate().compareTo(a.getCreateDate());
                })
                .limit(8)
                .map(this::toProductDto)
                .toList();

        // ===== Mã giảm giá đang khả dụng =====
        List<Map<String, Object>> discountCodes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (DiscountCode dc : discountCodeService.findByStatus(1)) {
            if (dc.getStartDate() != null && dc.getStartDate().isAfter(now)) {
                continue;
            }

            if (dc.getEndDate() != null && dc.getEndDate().isBefore(now)) {
                continue;
            }

            Map<String, Object> m = new HashMap<>();
            m.put("code", dc.getCode());
            m.put("detail", dc.getDetail() != null ? dc.getDetail() : "");
            m.put("percentage", dc.getPercentage());
            m.put("discountAmount", dc.getDiscountAmount());
            m.put("minimumAmountInCart", dc.getMinimumAmountInCart());

            discountCodes.add(m);
        }

        result.put("bestSellers", bestSellers);
        result.put("onSale", onSale);
        result.put("newProducts", newProducts);
        result.put("discountCodes", discountCodes);

        return ResponseEntity.ok(result);
    }

    // ===== AJAX: Lấy variants của sản phẩm =====
    @GetMapping("/product-variants/{productId}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getVariants(@PathVariable Integer productId) {
        List<ProductDetail> details = productDetailRepository.findByProductIdAndDeleteFlagFalse(productId);
        List<Map<String, Object>> variants = new ArrayList<>();

        for (ProductDetail pd : details) {
            if (pd.getQuantity() == null || pd.getQuantity() <= 0) {
                continue;
            }

            if (!pd.isVariantActive()) {
                continue;
            }

            Map<String, Object> m = new HashMap<>();
            m.put("id", pd.getId());
            m.put("size", pd.getSize() != null ? pd.getSize().getName() : "-");
            m.put("color", pd.getColor() != null ? pd.getColor().getName() : "-");
            m.put("price", pd.getPrice() != null ? pd.getPrice() : 0);
            m.put("quantity", pd.getQuantity());

            variants.add(m);
        }

        return ResponseEntity.ok(variants);
    }

    // ===== AJAX: Validate mã giảm giá =====
    @GetMapping("/validate-discount")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateDiscount(
            @RequestParam String code,
            @RequestParam double subtotal) {

        Map<String, Object> result = new HashMap<>();
        String validation = discountCodeService.validate(code, subtotal);

        if ("OK".equals(validation)) {
            DiscountCode dc = discountCodeService.findByCode(code);
            double discountAmount = discountCodeService.calculateDiscount(dc, subtotal);

            result.put("success", true);
            result.put("discountAmount", (long) discountAmount);
            result.put("finalTotal", (long) (subtotal - discountAmount));
        } else {
            result.put("success", false);
            result.put("message", validation);
        }

        return ResponseEntity.ok(result);
    }

    // ===== Danh sách đơn POS đang chờ =====
    @GetMapping("/waiting")
    public String waitingOrders(HttpSession session, Model model) {
        model.addAttribute("isAdmin", isAdmin(session));
        model.addAttribute("waitingOrders", posOrderService.findWaitingOrders());
        model.addAttribute("paymentMethods", paymentMethodRepository.findAll());
        return "pos/waiting";
    }

    // ===== Danh sách đơn POS đã hoàn thành =====
    // findCompletedOrders() đã có sẵn trong PosOrderService từ trước nhưng chưa
    // từng được route/template nào dùng tới — bổ sung trang này để không bỏ phí.
    @GetMapping("/completed")
    public String completedOrders(HttpSession session, Model model) {
        model.addAttribute("isAdmin", isAdmin(session));
        model.addAttribute("completedOrders", posOrderService.findCompletedOrders());
        return "pos/completed";
    }

    // ===== Tạo đơn POS đang chờ =====
    @PostMapping("/waiting")
    public String createWaitingOrder(
            @RequestParam(required = false) Integer customerId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerPhone,
            @RequestParam(required = false) String note,
            @RequestParam Integer paymentMethodId,
            @RequestParam(required = false) String discountCode,
            @RequestParam List<Integer> productDetailIds,
            @RequestParam List<Integer> quantities,
            HttpSession session,
            RedirectAttributes ra) {

        try {
            if (productDetailIds == null || quantities == null
                    || productDetailIds.isEmpty()
                    || productDetailIds.size() != quantities.size()) {
                throw new BusinessException("Dữ liệu sản phẩm không hợp lệ!");
            }

            for (Integer quantity : quantities) {
                if (quantity == null || quantity <= 0) {
                    throw new BusinessException("Số lượng sản phẩm phải lớn hơn 0!");
                }
            }

            Customer customer = null;

            if (customerId != null) {
                customer = customerRepository.findById(customerId).orElse(null);
            }

            if (customer == null && customerPhone != null && !customerPhone.isBlank()) {
                customer = customerRepository.findByPhoneNumber(customerPhone).orElse(null);
            }

            // Chỉ tạo Customer mới khi có SĐT (để lần sau còn tra lại được).
            // Khách hoàn toàn ẩn danh (không nhập SĐT) thì để bill.customer = null,
            // tránh insert account_id = NULL liên tục vào bảng Customer.
            if (customer == null && customerPhone != null && !customerPhone.isBlank()) {
                customer = new Customer();
                customer.setName(customerName != null && !customerName.isBlank()
                        ? customerName : "Khách lẻ");
                customer.setPhoneNumber(customerPhone);
                customer.setCode("KH" + java.util.UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8)
                        .toUpperCase());
                customer = customerRepository.save(customer);
            }

            double subtotal = 0;

            Bill bill = new Bill();
            bill.setCreateDate(LocalDateTime.now());
            bill.setUpdateDate(LocalDateTime.now());
            bill.setStatus(OrderStatus.PENDING.getValue());
            bill.setInvoiceType(2);
            bill.setPosStatus(PosOrderStatus.WAITING.getValue());
            bill.setCustomer(customer);
            bill.setBillingAddress("Tại quầy");
            bill.setShippingFee(0f);
            if (note != null && !note.isBlank()) bill.setNote(note.trim());

            for (int i = 0; i < productDetailIds.size(); i++) {
                ProductDetail pd = productDetailRepository.findById(productDetailIds.get(i)).orElse(null);

                if (pd == null) {
                    throw new BusinessException("Sản phẩm không tồn tại!");
                }

                if (!pd.isSellable()) {
                    throw new BusinessException(
                            "Sản phẩm \"" + pd.getProduct().getName() + "\" hiện không thể bán!");
                }

                int quantity = quantities.get(i);

                if (pd.getQuantity() == null || pd.getQuantity() < quantity) {
                    throw new BusinessException(
                            "Sản phẩm \"" + pd.getProduct().getName() + "\" chỉ còn "
                                    + (pd.getQuantity() != null ? pd.getQuantity() : 0) + " cái!");
                }

                subtotal += (pd.getFinalPrice() != null ? pd.getFinalPrice().doubleValue() : 0) * quantity;
            }

            double discountAmount = 0;
            DiscountCode appliedDiscount = null;

            if (discountCode != null && !discountCode.isBlank()) {
                Integer discountCustomerId = customer != null ? customer.getId() : null;
                String validation = discountCodeService.validate(discountCode, subtotal, discountCustomerId);

                if (!"OK".equals(validation)) {
                    throw new BusinessException(validation);
                }

                appliedDiscount = discountCodeService.findByCode(discountCode);
                discountAmount = discountCodeService.calculateDiscount(appliedDiscount, subtotal);

                if (discountAmount > subtotal) {
                    discountAmount = subtotal;
                }

                bill.setDiscountCode(appliedDiscount);
                bill.setPromotionPrice((float) discountAmount);
            }

            double finalAmount = subtotal - discountAmount;
            bill.setSubtotal((float) subtotal);
            bill.setAmount((float) finalAmount);

            bill = posOrderService.save(bill);
            bill.setCode("HD" + String.format("%05d", bill.getId()));
            bill = posOrderService.save(bill);

            for (int i = 0; i < productDetailIds.size(); i++) {
                ProductDetail pd = productDetailRepository.findById(productDetailIds.get(i)).orElse(null);

                if (pd == null) {
                    throw new BusinessException("Sản phẩm không tồn tại!");
                }

                BillDetail detail = new BillDetail();
                detail.setBill(bill);
                detail.setProductDetail(pd);
                detail.setMomentPrice(pd.getFinalPrice());
                detail.setQuantity(quantities.get(i));

                posOrderService.addDetail(detail);
            }

            ra.addFlashAttribute("successMsg", "Đã lưu đơn " + bill.getCode() + " vào danh sách chờ!");
            return "redirect:/pos/waiting";
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Lỗi: " + e.getMessage());
            return "redirect:/pos/create";
        }
    }

    // ===== AJAX: Chi tiết đơn POS đang chờ (cho modal thanh toán / sửa đơn) =====
    @GetMapping("/waiting/{id}/detail")
    @ResponseBody
    public ResponseEntity<?> waitingDetail(@PathVariable Integer id) {
        Bill bill = posOrderService.findById(id);

        if (bill == null || bill.getInvoiceType() == null || bill.getInvoiceType() != 2) {
            return ResponseEntity.status(404).body(Map.of("error", "Không tìm thấy đơn POS!"));
        }

        return ResponseEntity.ok(billToDto(bill));
    }

    // ===== AJAX: Thêm sản phẩm vào đơn POS đang chờ (mua thêm) =====
    @PostMapping("/waiting/{id}/items")
    @ResponseBody
    public ResponseEntity<?> addItemToWaitingOrder(
            @PathVariable Integer id,
            @RequestParam Integer productDetailId,
            @RequestParam Integer quantity) {

        try {
            Bill bill = posOrderService.addItem(id, productDetailId, quantity);
            return ResponseEntity.ok(billToDto(bill));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ===== AJAX: Sửa số lượng 1 dòng trong đơn POS đang chờ (quantity <= 0 = xóa dòng) =====
    @PostMapping("/waiting/{id}/items/{detailId}/quantity")
    @ResponseBody
    public ResponseEntity<?> setWaitingItemQuantity(
            @PathVariable Integer id,
            @PathVariable Integer detailId,
            @RequestParam Integer quantity) {

        try {
            Bill bill = posOrderService.setItemQuantity(id, detailId, quantity);
            return ResponseEntity.ok(billToDto(bill));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ===== AJAX: Áp mã giảm giá cho đơn POS đang chờ (áp sau khi đã tạo đơn chờ) =====
    @PostMapping("/waiting/{id}/discount")
    @ResponseBody
    public ResponseEntity<?> applyDiscountToWaitingOrder(
            @PathVariable Integer id,
            @RequestParam String code) {

        try {
            Bill bill = posOrderService.applyDiscount(id, code);
            return ResponseEntity.ok(billToDto(bill));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ===== AJAX: Hủy mã giảm giá đang áp trên đơn POS đang chờ =====
    @PostMapping("/waiting/{id}/discount/remove")
    @ResponseBody
    public ResponseEntity<?> removeDiscountFromWaitingOrder(@PathVariable Integer id) {
        try {
            Bill bill = posOrderService.removeDiscount(id);
            return ResponseEntity.ok(billToDto(bill));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> billToDto(Bill bill) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", bill.getId());
        dto.put("code", bill.getCode());
        dto.put("customerName", bill.getCustomer() != null ? bill.getCustomer().getName() : "Khách lẻ");
        dto.put("customerPhone", bill.getCustomer() != null ? bill.getCustomer().getPhoneNumber() : "");
        dto.put("subtotal", bill.getSubtotal());
        dto.put("promotionPrice", bill.getPromotionPrice());
        dto.put("amount", bill.getAmount());
        dto.put("discountCode", bill.getDiscountCode() != null ? bill.getDiscountCode().getCode() : null);

        List<Map<String, Object>> items = new ArrayList<>();

        for (BillDetail d : billDetailRepository.findByBillId(bill.getId())) {
            Map<String, Object> m = new HashMap<>();
            ProductDetail pd = d.getProductDetail();
            m.put("billDetailId", d.getId());
            m.put("productName", pd != null && pd.getProduct() != null ? pd.getProduct().getName() : "");
            m.put("size", pd != null && pd.getSize() != null ? pd.getSize().getName() : "-");
            m.put("color", pd != null && pd.getColor() != null ? pd.getColor().getName() : "-");
            m.put("price", d.getMomentPrice());
            m.put("quantity", d.getQuantity());
            m.put("stock", pd != null && pd.getQuantity() != null ? pd.getQuantity() : 0);
            items.add(m);
        }

        dto.put("items", items);
        return dto;
    }

    @GetMapping("/pay/{id}")
    public String payForm(@PathVariable Integer id, HttpSession session, Model model, RedirectAttributes ra) {
        Bill bill = posOrderService.findById(id);

        if (bill == null) {
            ra.addFlashAttribute("error", "Không tìm thấy đơn POS!");
            return "redirect:/pos/waiting";
        }

        if (!PosOrderStatus.WAITING.matches(bill.getPosStatus())) {
            ra.addFlashAttribute("error", "Đơn này không còn ở trạng thái chờ thanh toán!");
            return "redirect:/pos/waiting";
        }

        model.addAttribute("isAdmin", isAdmin(session));
        model.addAttribute("bill", bill);
        model.addAttribute("paymentMethods", paymentMethodRepository.findAll());

        return "pos/pay";
    }

    // ===== Thanh toán đơn POS =====
    @PostMapping("/pay/{id}")
    public String pay(
            @PathVariable Integer id,
            @RequestParam Integer paymentMethodId,
            HttpSession session,
            RedirectAttributes ra) {

        try {
            Account account = (Account) session.getAttribute("account");
            Integer staffId = null;

            if (account != null) {
                Staff staff = staffRepository.findByAccountId(account.getId());

                if (staff != null) {
                    staffId = staff.getId();
                }
            }

            Bill bill = posOrderService.pay(id, paymentMethodId, staffId);
            ra.addFlashAttribute("success", "Thanh toán đơn " + bill.getCode() + " thành công!");

            return "redirect:/pos/waiting";
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/pos/waiting";
        }
    }

    // ===== Hủy đơn POS đang chờ =====
    @PostMapping("/cancel/{id}")
    public String cancel(@PathVariable Integer id, RedirectAttributes ra) {
        try {
            posOrderService.cancel(id);
            ra.addFlashAttribute("success", "Đã hủy đơn POS!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/pos/waiting";
    }
}