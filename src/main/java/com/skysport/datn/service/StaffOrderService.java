package com.skysport.datn.service;

import com.skysport.datn.entity.*;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.exception.BusinessException;
import com.skysport.datn.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Business logic cho bán hàng tại quầy (đơn giản, tạo + hoàn thành ngay).
 * Tách ra từ StaffOrderController — controller chỉ nên gọi service rồi map kết quả
 * sang view/redirect, không nên tự thao tác Repository + tính tiền + khóa tồn kho.
 */
@Service
@RequiredArgsConstructor
public class StaffOrderService {

    private final BillRepository billRepository;
    private final BillDetailRepository billDetailRepository;
    private final CustomerRepository customerRepository;
    private final ProductDetailRepository productDetailRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final DiscountCodeService discountCodeService;
    private final StaffRepository staffRepository;

    @Transactional
    public Bill createOrder(Integer customerId,
                            String customerName,
                            String customerPhone,
                            Integer paymentMethodId,
                            String discountCode,
                            List<Integer> productDetailIds,
                            List<Integer> quantities,
                            Integer accountId) {

        // 0. Validate dữ liệu đầu vào
        if (productDetailIds == null || quantities == null
                || productDetailIds.isEmpty() || productDetailIds.size() != quantities.size()) {
            throw new BusinessException("Dữ liệu sản phẩm không hợp lệ!");
        }
        for (Integer q : quantities) {
            if (q == null || q <= 0) {
                throw new BusinessException("Số lượng sản phẩm phải lớn hơn 0!");
            }
        }

        // 1. Xác định khách hàng
        Customer customer = resolveCustomer(customerId, customerName, customerPhone);

        // 2. Khóa & kiểm tra tồn kho — dùng PESSIMISTIC_WRITE để tránh race condition
        //    khi 2 quầy bán cùng sản phẩm đồng thời. Giữ lại map để dùng tiếp ở bước 3 & 6
        //    (cùng transaction, tránh đọc lại nhiều lần và đảm bảo dữ liệu nhất quán).
        Map<Integer, ProductDetail> lockedDetails = new LinkedHashMap<>();
        for (int i = 0; i < productDetailIds.size(); i++) {
            Integer pdId = productDetailIds.get(i);
            ProductDetail pd = productDetailRepository.findByIdForUpdate(pdId).orElse(null);
            if (pd == null) throw new BusinessException("Sản phẩm không tồn tại!");
            int qty = quantities.get(i);
            if (pd.getQuantity() == null || pd.getQuantity() < qty) {
                throw new BusinessException("Sản phẩm \"" + pd.getProduct().getName()
                        + "\" chỉ còn " + (pd.getQuantity() != null ? pd.getQuantity() : 0) + " cái!");
            }
            lockedDetails.put(pdId, pd);
        }

        // 3. Tính tiền — dùng getFinalPrice() để áp đúng giá sale nếu sản phẩm đang khuyến mãi,
        //    nhất quán với CheckoutService (online) và PosOrderService (POS waiting).
        double subtotal = 0;
        for (int i = 0; i < productDetailIds.size(); i++) {
            ProductDetail pd = lockedDetails.get(productDetailIds.get(i));
            subtotal += (pd.getFinalPrice() != null ? pd.getFinalPrice() : 0) * quantities.get(i);
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
                throw new BusinessException(validation);
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
            detail.setMomentPrice(pd.getFinalPrice()); // giá thực tế tại thời điểm bán, kể cả khuyến mãi
            detail.setQuantity(quantities.get(i));
            billDetailRepository.save(detail);

            pd.setQuantity(pd.getQuantity() - quantities.get(i));
            productDetailRepository.save(pd);
        }

        // 7. Ghi lịch sử
        Staff staff = accountId != null ? staffRepository.findByAccountId(accountId) : null;

        OrderStatusHistory history = new OrderStatusHistory();
        history.setBill(bill);
        history.setStatus(OrderStatus.COMPLETED.getValue());
        history.setNote("Đơn bán tại quầy" + (staff != null ? " - " + staff.getName() : ""));
        history.setCreatedDate(LocalDateTime.now());
        history.setStaff(staff);
        orderStatusHistoryRepository.save(history);

        // 8. Tăng lượt sử dụng mã giảm giá
        if (appliedDiscount != null) {
            discountCodeService.incrementUsage(appliedDiscount.getId());
        }

        return bill;
    }

    private Customer resolveCustomer(Integer customerId, String customerName, String customerPhone) {
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
            throw new BusinessException("Vui lòng nhập SĐT hoặc tên khách hàng!");
        }
        return customer;
    }
}