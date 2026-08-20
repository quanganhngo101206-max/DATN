package com.skysport.datn.controller.staff;

import com.skysport.datn.entity.*;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.repository.*;
import com.skysport.datn.service.DiscountCodeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/staff/order")
public class StaffOrderController {

    @Autowired private BillRepository billRepository;
    @Autowired private BillDetailRepository billDetailRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductDetailRepository productDetailRepository;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired private ImageRepository imageRepository;
    @Autowired private DiscountCodeService discountCodeService;
    @Autowired private StaffRepository staffRepository;

    // ===== Trang tạo đơn tại quầy =====
    @GetMapping("/create")
    public String createForm(HttpSession session, Model model) {
        Account account = (Account) session.getAttribute("account");
        if (account != null) {
            Staff staff = staffRepository.findByAccountId(account.getId());
            model.addAttribute("staff", staff);
        }

        List<Product> products = productRepository.findByDeleteFlagFalse().stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .toList();

        // Lấy ảnh đại diện cho mỗi sản phẩm
        Map<Integer, String> productImages = new HashMap<>();
        for (Product p : products) {
            List<Image> imgs = imageRepository.findByProductId(p.getId());
            if (!imgs.isEmpty()) productImages.put(p.getId(), imgs.get(0).getLink());
        }

        model.addAttribute("products", products);
        model.addAttribute("productImages", productImages);
        model.addAttribute("paymentMethods", paymentMethodRepository.findAll());
        return "staff/order/create";
    }

    // ===== AJAX: Gợi ý khách hàng theo SĐT / Tên / Mã KH =====
    @GetMapping("/search-customer")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchCustomer(@RequestParam String keyword) {
        List<Map<String, Object>> result = new ArrayList<>();
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) return ResponseEntity.ok(result);

        List<Customer> customers = customerRepository.searchByKeyword(kw);
        for (Customer c : customers) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("code", c.getCode() != null ? c.getCode() : "");
            m.put("name", c.getName() != null ? c.getName() : "");
            m.put("phone", c.getPhoneNumber() != null ? c.getPhoneNumber() : "");
            m.put("email", c.getEmail() != null ? c.getEmail() : "");
            result.add(m);
            if (result.size() >= 10) break;
        }
        return ResponseEntity.ok(result);
    }

    // ===== AJAX: Gợi ý sản phẩm khi mới mở trang (bán chạy / đang sale / mới) =====
    @GetMapping("/suggested-products")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> suggestedProducts() {
        Map<String, Object> result = new HashMap<>();

        List<Product> allActive = productRepository.findByDeleteFlagFalse().stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .toList();
        Map<Integer, Product> productMap = new HashMap<>();
        for (Product p : allActive) productMap.put(p.getId(), p);

        // Sản phẩm bán chạy nhất (top 8 theo tổng số lượng đã bán)
        List<Map<String, Object>> bestSellers = new ArrayList<>();
        for (Object[] row : billDetailRepository.findBestSellingProductIds()) {
            Integer productId = (Integer) row[0];
            Product p = productMap.get(productId);
            if (p == null) continue;
            bestSellers.add(toProductDto(p));
            if (bestSellers.size() >= 8) break;
        }

        // Sản phẩm đang sale (còn hiệu lực)
        List<Map<String, Object>> onSale = new ArrayList<>();
        List<Integer> saleIds = productDetailRepository.findProductIdsOnSale(LocalDateTime.now());
        for (Integer productId : saleIds) {
            Product p = productMap.get(productId);
            if (p == null) continue;
            onSale.add(toProductDto(p));
            if (onSale.size() >= 8) break;
        }

        // Sản phẩm mới (sắp xếp theo ngày tạo)
        List<Map<String, Object>> newProducts = allActive.stream()
                .sorted((a, b) -> {
                    if (a.getCreateDate() == null) return 1;
                    if (b.getCreateDate() == null) return -1;
                    return b.getCreateDate().compareTo(a.getCreateDate());
                })
                .limit(8)
                .map(this::toProductDto)
                .toList();

        // Mã giảm giá đang khả dụng
        List<Map<String, Object>> discountCodes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (DiscountCode dc : discountCodeService.findByStatus(1)) {
            if (dc.getStartDate() != null && dc.getStartDate().isAfter(now)) continue;
            if (dc.getEndDate() != null && dc.getEndDate().isBefore(now)) continue;
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

    // ===== AJAX: Tìm kiếm sản phẩm theo tên/mã, kèm nhãn gợi ý (bán chạy/sale/mới) =====
    @GetMapping("/search-products")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchProducts(@RequestParam String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Product> allActive = productRepository.findByDeleteFlagFalse().stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .filter(p -> kw.isEmpty()
                        || (p.getName() != null && p.getName().toLowerCase().contains(kw))
                        || (p.getCode() != null && p.getCode().toLowerCase().contains(kw)))
                .toList();

        // Tập id bán chạy & đang sale để gắn nhãn
        Set<Integer> bestSellerIds = new HashSet<>();
        for (Object[] row : billDetailRepository.findBestSellingProductIds()) {
            bestSellerIds.add((Integer) row[0]);
            if (bestSellerIds.size() >= 8) break;
        }
        Set<Integer> onSaleIds = new HashSet<>(productDetailRepository.findProductIdsOnSale(LocalDateTime.now()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Product p : allActive) {
            Map<String, Object> m = toProductDto(p);
            m.put("isBestSeller", bestSellerIds.contains(p.getId()));
            m.put("isOnSale", onSaleIds.contains(p.getId()));
            boolean isNew = p.getCreateDate() != null
                    && p.getCreateDate().isAfter(LocalDateTime.now().minusDays(30));
            m.put("isNew", isNew);
            result.add(m);
            if (result.size() >= 30) break;
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

    // ===== AJAX: Lấy variants của sản phẩm (size, color, quantity, price) =====
    @GetMapping("/product-variants/{productId}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getVariants(@PathVariable Integer productId) {
        List<ProductDetail> details = productDetailRepository
                .findByProductIdAndDeleteFlagFalse(productId);

        List<Map<String, Object>> variants = new ArrayList<>();
        for (ProductDetail pd : details) {
            if (pd.getQuantity() == null || pd.getQuantity() <= 0) continue;
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

    // ===== Xử lý tạo đơn =====
    @PostMapping("/create")
    @Transactional
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
            // 0. Validate dữ liệu đầu vào
            if (productDetailIds == null || quantities == null
                    || productDetailIds.isEmpty() || productDetailIds.size() != quantities.size()) {
                throw new RuntimeException("Dữ liệu sản phẩm không hợp lệ!");
            }
            for (Integer q : quantities) {
                if (q == null || q <= 0) {
                    throw new RuntimeException("Số lượng sản phẩm phải lớn hơn 0!");
                }
            }

            // 1. Xác định khách hàng
            Customer customer = null;
            if (customerId != null) {
                customer = customerRepository.findById(customerId).orElse(null);
            }
            if (customer == null && customerPhone != null && !customerPhone.isBlank()) {
                customer = customerRepository.findByPhoneNumber(customerPhone).orElse(null);
            }
            if (customer == null && ((customerPhone != null && !customerPhone.isBlank()) || (customerName != null && !customerName.isBlank()))) {
                // Tạo khách lẻ mới
                customer = new Customer();
                customer.setName(customerName != null && !customerName.isBlank() ? customerName : "Khách lẻ");
                customer.setPhoneNumber(customerPhone != null ? customerPhone : "");
                customer.setCode("KH" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
                customer = customerRepository.save(customer);
            }
            if (customer == null) {
                throw new RuntimeException("Vui lòng nhập SĐT hoặc tên khách hàng!");
            }

            // 2. Khóa & kiểm tra tồn kho — dùng PESSIMISTIC_WRITE để tránh race condition
            //    khi 2 quầy bán cùng sản phẩm đồng thời. Giữ lại map để dùng tiếp ở bước 3 & 6
            //    (cùng transaction, tránh đọc lại nhiều lần và đảm bảo dữ liệu nhất quán).
            Map<Integer, ProductDetail> lockedDetails = new LinkedHashMap<>();
            for (int i = 0; i < productDetailIds.size(); i++) {
                Integer pdId = productDetailIds.get(i);
                ProductDetail pd = productDetailRepository.findByIdForUpdate(pdId).orElse(null);
                if (pd == null) throw new RuntimeException("Sản phẩm không tồn tại!");
                int qty = quantities.get(i);
                if (pd.getQuantity() == null || pd.getQuantity() < qty) {
                    throw new RuntimeException("Sản phẩm \"" + pd.getProduct().getName()
                            + "\" chỉ còn " + (pd.getQuantity() != null ? pd.getQuantity() : 0) + " cái!");
                }
                lockedDetails.put(pdId, pd);
            }

            // 3. Tính tiền
            double subtotal = 0;
            for (int i = 0; i < productDetailIds.size(); i++) {
                ProductDetail pd = lockedDetails.get(productDetailIds.get(i));
                subtotal += (pd.getPrice() != null ? pd.getPrice() : 0) * quantities.get(i);
            }

            // 4. Mã giảm giá — kiểm tra cả việc khách này đã dùng mã chưa
            double discountAmount = 0;
            DiscountCode appliedDiscount = null;
            if (discountCode != null && !discountCode.isBlank()) {
                String validation = discountCodeService.validate(discountCode, subtotal, customer.getId());
                if ("OK".equals(validation)) {
                    appliedDiscount = discountCodeService.findByCode(discountCode);
                    discountAmount = discountCodeService.calculateDiscount(appliedDiscount, subtotal);
                    if (discountAmount > subtotal) discountAmount = subtotal;
                } else {
                    throw new RuntimeException(validation);
                }
            }

            double finalAmount = subtotal - discountAmount;

            // 5. Tạo Bill — invoiceType=2 là bán tại quầy
            Bill bill = new Bill();
            bill.setCreateDate(LocalDateTime.now());
            bill.setUpdateDate(LocalDateTime.now());
            bill.setStatus(OrderStatus.COMPLETED.getValue()); // Hoàn thành ngay
            bill.setInvoiceType(2); // 2 = tại quầy
            bill.setAmount((float) finalAmount);
            bill.setSubtotal((float) subtotal);
            bill.setShippingFee(0f);
            bill.setCustomer(customer);
            bill.setBillingAddress("Tại quầy");

            if (appliedDiscount != null) {
                bill.setDiscountCode(appliedDiscount);
                bill.setPromotionPrice((float) discountAmount);
            }

            Payment pm = paymentMethodRepository.findById(paymentMethodId).orElse(null);
            bill.setPaymentMethod(pm);

            bill = billRepository.save(bill);
            bill.setCode("HD" + String.format("%05d", bill.getId()));
            bill = billRepository.save(bill);

            // 6. Tạo BillDetail + trừ tồn kho (dùng ProductDetail đã khóa ở bước 2)
            for (int i = 0; i < productDetailIds.size(); i++) {
                ProductDetail pd = lockedDetails.get(productDetailIds.get(i));

                BillDetail detail = new BillDetail();
                detail.setBill(bill);
                detail.setProductDetail(pd);
                detail.setMomentPrice(pd.getPrice());
                detail.setQuantity(quantities.get(i));
                billDetailRepository.save(detail);

                pd.setQuantity(pd.getQuantity() - quantities.get(i));
                productDetailRepository.save(pd);
            }

            // 7. Ghi lịch sử
            Account account = (Account) session.getAttribute("account");
            Staff staff = account != null ? staffRepository.findByAccountId(account.getId()) : null;

            OrderStatusHistory history = new OrderStatusHistory();
            history.setBill(bill);
            history.setStatus(OrderStatus.COMPLETED.getValue());
            history.setNote("Đơn bán tại quầy" + (staff != null ? " - " + staff.getName() : ""));
            history.setCreatedDate(LocalDateTime.now());
            history.setStaff(staff);
            orderStatusHistoryRepository.save(history);

            // 8. Trừ lượt dùng mã giảm giá
            if (appliedDiscount != null) {
                discountCodeService.decreaseUsage(appliedDiscount.getId());
            }

            ra.addFlashAttribute("successMsg", "Tạo đơn " + bill.getCode() + " thành công!");
            return "redirect:/staff/bill/detail/" + bill.getId();

        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Lỗi: " + e.getMessage());
            return "redirect:/staff/order/create";
        }
    }
}