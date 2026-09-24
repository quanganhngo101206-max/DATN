package com.skysport.datn.service;

import com.skysport.datn.controller.customer.CartController;
import com.skysport.datn.dto.CheckoutRequest;
import com.skysport.datn.dto.DiscountApplyResult;
import com.skysport.datn.dto.ShippingFeeResponse;
import com.skysport.datn.entity.*;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.exception.BusinessException;
import com.skysport.datn.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Business logic cho checkout — tách ra từ CheckoutController.
 * Controller chỉ giữ phần thuộc về HTTP: session, redirect.
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final BillRepository billRepository;
    private final BillDetailRepository billDetailRepository;
    private final CustomerRepository customerRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final DiscountCodeService discountCodeService;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final ProductDetailRepository productDetailRepository;
    private final WardRepository wardRepository;
    private final ProvinceRepository provinceRepository;
    private final ShippingFeeService shippingFeeService;
    private final CartService cartService;
    private final AddressShippingRepository addressShippingRepository;

    /**
     * Phí vận chuyển phải được tính lại ở backend từ tỉnh nhận hàng và
     * subtotal được tính từ giỏ hàng, không lấy phí do frontend gửi lên.
     */
    public double calculateShipping(double subtotal, Integer provinceId) {
        if (provinceId == null) {
            return 0;
        }

        ShippingFeeResponse response = shippingFeeService.calculate(
                provinceId,
                BigDecimal.valueOf(subtotal)
        );

        return response.getShippingFee().doubleValue();
    }

    /**
     * AJAX validate + tính thử mã giảm giá. Không lưu gì xuống DB — chỉ trả số liệu
     * để hiển thị. Việc lưu appliedDiscountCode vào session vẫn do controller làm
     * (đây là HTTP session, không phải việc của service).
     */
    public DiscountApplyResult applyDiscount(
            String code,
            double subtotal,
            Integer provinceId,
            Integer accountId) {

        double shipping = calculateShipping(subtotal, provinceId);
        double total = subtotal + shipping;

        Integer customerId = null;

        if (accountId != null) {
            Customer customer = customerRepository.findByAccountId(accountId);

            if (customer != null) {
                customerId = customer.getId();
            }
        }

        String validation = discountCodeService.validate(code, subtotal, customerId);

        if (!"OK".equals(validation)) {
            return DiscountApplyResult.builder()
                    .success(false)
                    .message(validation)
                    .build();
        }

        DiscountCode discount = discountCodeService.findByCode(code);

        double discountAmount = discountCodeService.calculateDiscount(discount, subtotal);

        if (discountAmount > total) {
            discountAmount = total;
        }

        double finalTotal = total - discountAmount;

        return DiscountApplyResult.builder()
                .success(true)
                .message("Áp dụng thành công!")
                .discountAmount((long) discountAmount)
                .finalTotal((long) finalTotal)
                .shipping((long) shipping)
                .build();
    }

    @Transactional
    public Bill placeOrder(
            CheckoutRequest request,
            Map<Integer, CartController.CartItem> cart,
            Integer accountId,
            String discountCodeFromSession) {

        // ---------------------------------------------------------------
        // Xác định Customer — tách rõ 2 nhánh:
        //
        // NHÁNH 1: Khách đã đăng nhập → lấy Customer theo accountId.
        //          Nếu account không có Customer tương ứng → lỗi nghiệp vụ.
        //
        // NHÁNH 2: Khách vãng lai (guest) → LUÔN tạo Customer mới, không
        //          tra cứu theo SĐT. Tra cứu theo SĐT có thể gắn đơn nhầm
        //          vào Customer của người khác, gây sai lịch sử đơn hàng,
        //          sai kiểm tra voucher (hasCustomerUsedDiscount), sai thống kê.
        // ---------------------------------------------------------------
        Customer customer;

        if (accountId != null) {
            // Nhánh logged-in
            customer = customerRepository.findByAccountId(accountId);
            if (customer == null) {
                throw new BusinessException(
                        "Không tìm thấy thông tin khách hàng cho tài khoản này!"
                );
            }
        } else {
            // Nhánh guest — tạo mới, không tra cứu SĐT
            customer = createGuestCustomer(request);
        }

        /*
         * Nếu checkout chọn địa chỉ đã lưu:
         * - Kiểm tra địa chỉ tồn tại
         * - Kiểm tra địa chỉ thuộc đúng customer
         * - Lấy lại toàn bộ thông tin từ DB
         * Không tin dữ liệu address/province/ward do frontend tự gửi.
         */
        if (request.getAddressId() != null) {
            AddressShipping selectedAddress = addressShippingRepository
                    .findById(request.getAddressId())
                    .orElse(null);

            if (selectedAddress == null) {
                throw new BusinessException(
                        "Địa chỉ giao hàng không tồn tại!"
                );
            }

            if (selectedAddress.getCustomer() == null
                    || selectedAddress.getCustomer().getId() == null
                    || !selectedAddress.getCustomer()
                    .getId()
                    .equals(customer.getId())) {

                throw new BusinessException(
                        "Địa chỉ giao hàng không thuộc tài khoản của bạn!"
                );
            }

            request.setFullName(selectedAddress.getReceiverName());
            request.setPhoneNumber(selectedAddress.getReceiverPhone());
            request.setAddress(selectedAddress.getAddress());
            request.setProvinceId(selectedAddress.getProvinceId());
            request.setWardId(selectedAddress.getWardId());
        }

        /*
         * Kiểm tra địa chỉ sau khi đã xác định:
         * - Địa chỉ lưu trong tài khoản
         * - Hoặc địa chỉ mới nhập trực tiếp
         */
        if (request.getFullName() == null
                || request.getFullName().isBlank()) {

            throw new BusinessException(
                    "Vui lòng nhập tên người nhận!"
            );
        }

        if (request.getPhoneNumber() == null
                || request.getPhoneNumber().isBlank()) {

            throw new BusinessException(
                    "Vui lòng nhập số điện thoại người nhận!"
            );
        }

        if (request.getAddress() == null
                || request.getAddress().isBlank()) {

            throw new BusinessException(
                    "Vui lòng nhập địa chỉ giao hàng!"
            );
        }

        if (request.getProvinceId() == null) {
            throw new BusinessException(
                    "Vui lòng chọn tỉnh/thành phố!"
            );
        }

        if (request.getWardId() == null) {
            throw new BusinessException(
                    "Vui lòng chọn phường/xã!"
            );
        }

        // Ghép địa chỉ đầy đủ = số nhà + phường/xã + tỉnh/thành
        String fullAddress = request.getAddress();

        Province province = provinceRepository.findById(request.getProvinceId()).orElse(null);
        if (province == null) {
            throw new BusinessException("Tỉnh/thành phố không tồn tại!");
        }

        Ward ward = wardRepository.findById(request.getWardId()).orElse(null);
        if (ward == null) {
            throw new BusinessException("Phường/xã không tồn tại!");
        }

        // Kiểm tra phường/xã phải thuộc tỉnh/thành đã chọn — tránh địa chỉ không hợp lệ
        if (!Objects.equals(ward.getProvinceId(), province.getId())) {
            throw new BusinessException("Phường/xã không thuộc tỉnh/thành đã chọn!");
        }

        fullAddress += ", " + ward.getName() + ", " + province.getName();

        /*
         * Tạo Bill skeleton — amount/subtotal/shippingFee/discount sẽ được
         * tính và set AFTER khi lock ProductDetail để lấy giá chính xác từ DB.
         * Không set các giá trị tiền ở đây.
         */
        Bill bill = new Bill();

        bill.setCreateDate(LocalDateTime.now());
        bill.setUpdateDate(LocalDateTime.now());
        bill.setStatus(OrderStatus.PENDING.getValue());
        bill.setBillingAddress(fullAddress);
        // Lưu người nhận theo đúng thông tin khách nhập/chọn lúc đặt hàng,
        // không lấy từ Customer (chủ tài khoản) vì có thể là người khác.
        bill.setReceiverName(request.getFullName());
        bill.setReceiverPhone(request.getPhoneNumber());
        bill.setInvoiceType(1);
        bill.setNote(request.getNote());
        bill.setCustomer(customer);

        // Phương thức thanh toán
        String requestedMethod = request.getPaymentMethod();

        List<Payment> methods = paymentMethodRepository.findAll();

        Payment paymentMethod = methods.stream()
                .filter(m -> {
                    if (m.getName() == null) {
                        return false;
                    }
                    String upper = m.getName().toUpperCase();
                    if ("BANKING".equals(requestedMethod)) {
                        return upper.contains("CHUY")
                                || upper.contains("BANK")
                                || upper.contains("KHO");
                    }

                    if ("COD".equals(requestedMethod)) {
                        return upper.contains("TIỀN")
                                || upper.contains("MẶT")
                                || upper.contains("COD");
                    }

                    return false;
                })
                .findFirst().orElse(null);

        if (paymentMethod == null) {
            throw new BusinessException(
                    "Phương thức thanh toán không hợp lệ!"
            );
        }

        bill.setPaymentMethod(paymentMethod);

        // Khóa từng dòng tồn kho (PESSIMISTIC_WRITE)
        // Đồng thời lấy giá chính xác từ DB (getFinalPrice) để tính subtotal thực.
        // KHÔNG dùng CartItem.price vì giá có thể thay đổi sau khi khách thêm vào giỏ.
        Map<Integer, ProductDetail> lockedDetails = new LinkedHashMap<>();
        Map<Integer, Double> dbPriceByDetailId = new LinkedHashMap<>();

        // Lock theo thứ tự productDetailId tăng dần: 2 request mua cùng lúc nhiều biến thể
        // sẽ lock cùng thứ tự -> không thể chờ vòng tròn (deadlock).
        List<CartController.CartItem> itemsInLockOrder = cart.values().stream()
                .sorted(Comparator.comparing(CartController.CartItem::getProductDetailId))
                .toList();

        for (CartController.CartItem item : itemsInLockOrder) {

            ProductDetail pd = productDetailRepository.findByIdForUpdate(item.getProductDetailId()).orElse(null);

            if (pd == null) {
                throw new BusinessException(
                        "Sản phẩm không còn tồn tại trong hệ thống!"
                );
            }

            var prod = pd.getProduct();

            if (prod == null || Boolean.TRUE.equals(prod.getDeleteFlag()) || prod.getStatus() == null || prod.getStatus() != 1) {
                throw new BusinessException(
                        "Sản phẩm \"" + item.getProductName()
                                + "\" hiện không còn được bán!"
                );
            }

            if (!pd.isVariantStatusActive()) {
                throw new BusinessException(
                        "Biến thể \"" + item.getProductName()
                                + "\" hiện đang tạm dừng bán!"
                );
            }

            if (!pd.isSizeActive()) {
                throw new BusinessException(
                        "Size của sản phẩm \"" + item.getProductName()
                                + "\" hiện không còn được bán!"
                );
            }

            if (!pd.isColorActive()) {
                throw new BusinessException(
                        "Màu của sản phẩm \"" + item.getProductName()
                                + "\" hiện không còn được bán!"
                );
            }

            if (item.getQuantity() <= 0) {
                throw new BusinessException(
                        "Số lượng sản phẩm không hợp lệ!"
                );
            }

            if (pd.getQuantity() == null
                    || pd.getQuantity() < item.getQuantity()) {

                throw new BusinessException(
                        "Sản phẩm \"" + item.getProductName()
                                + "\" chỉ còn "
                                + (pd.getQuantity() != null
                                ? pd.getQuantity()
                                : 0)
                                + " sản phẩm trong kho!"
                );
            }

            lockedDetails.put(item.getProductDetailId(), pd);

            // Lấy giá thực tế từ DB tại thời điểm checkout (bao gồm discount sản phẩm nếu có)
            Float finalPrice = pd.getFinalPrice();
            dbPriceByDetailId.put(item.getProductDetailId(),
                    finalPrice != null ? finalPrice.doubleValue() : 0.0);
        }

        // Tính subtotal từ giá DB (đã lock) — không tin CartItem.price từ session
        double subtotal = cart.values().stream()
                .mapToDouble(i -> dbPriceByDetailId.getOrDefault(i.getProductDetailId(), 0.0)
                        * i.getQuantity())
                .sum();

        // Tính lại shipping, discount, finalAmount dựa trên subtotal thực từ DB
        double shipping = calculateShipping(subtotal, request.getProvinceId());
        double total    = subtotal + shipping;

        double discountAmount = 0;
        DiscountCode appliedDiscount = null;

        String codeToUse = discountCodeFromSession != null
                ? discountCodeFromSession
                : request.getDiscountCode();

        if (codeToUse != null && !codeToUse.isBlank()) {
            String validation = discountCodeService.validate(codeToUse, subtotal, customer.getId());
            if ("OK".equals(validation)) {
                appliedDiscount = discountCodeService.findByCode(codeToUse);
                discountAmount  = discountCodeService.calculateDiscount(appliedDiscount, subtotal);
                if (discountAmount > total) {
                    discountAmount = total;
                }
            }
        }

        double finalAmount = total - discountAmount;

        // Cập nhật lại Bill với subtotal/shipping/amount đã tính từ giá DB
        bill.setSubtotal((float) subtotal);
        bill.setShippingFee((float) shipping);
        bill.setAmount((float) finalAmount);

        if (appliedDiscount != null) {
            bill.setDiscountCode(appliedDiscount);
            bill.setPromotionPrice((float) discountAmount);
        } else {
            bill.setDiscountCode(null);
            bill.setPromotionPrice(null);
        }

        bill = billRepository.save(bill);

        bill.setCode("HD" + String.format("%05d", bill.getId()));

        bill = billRepository.save(bill);

        // Tạo BillDetail + trừ tồn kho
        for (CartController.CartItem item : cart.values()) {

            ProductDetail productDetail = lockedDetails.get(item.getProductDetailId());

            BillDetail detail = new BillDetail();

            detail.setBill(bill);
            detail.setProductDetail(productDetail);
            // momentPrice lấy từ giá DB tại thời điểm checkout — không dùng CartItem.price
            double priceAtCheckout = dbPriceByDetailId.getOrDefault(item.getProductDetailId(), 0.0);
            detail.setMomentPrice((float) priceAtCheckout);
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

        // COD: đặt hàng thành công thì tính lượt sử dụng voucher ngay
        if ("COD".equals(requestedMethod)
                && appliedDiscount != null) {

            discountCodeService.incrementUsage(appliedDiscount.getId());
        }
        return bill;
    }

    /**
     * Tạo Customer mới cho đơn hàng guest (khách vãng lai chưa đăng nhập).
     *
     * KHÔNG tra cứu SĐT trước khi tạo — dù có Customer trùng SĐT trong DB,
     * guest vẫn được tạo bản ghi riêng. Lý do:
     * - Guest B nhập SĐT của Customer A không có nghĩa là B chính là A.
     * - Gắn nhầm đơn của B vào A gây sai lịch sử mua hàng, sai kiểm tra
     *   voucher (hasCustomerUsedDiscount dùng customerId), sai thống kê.
     * - Guest Customer không có account (account = null), dễ phân biệt.
     *
     * Lưu 2 lần: lần 1 để có ID do DB sinh, lần 2 để set code = "KH{id}".
     */
    private Customer createGuestCustomer(CheckoutRequest request) {
        Customer guest = new Customer();
        guest.setName(request.getFullName());
        guest.setPhoneNumber(request.getPhoneNumber());
        guest.setEmail(
                request.getEmail() != null && !request.getEmail().isBlank()
                        ? request.getEmail()
                        : null
        );
        // account = null → đây là guest, không phải tài khoản đăng ký

        guest = customerRepository.save(guest);
        guest.setCode("KH" + String.format("%05d", guest.getId()));
        return customerRepository.save(guest);
    }
}