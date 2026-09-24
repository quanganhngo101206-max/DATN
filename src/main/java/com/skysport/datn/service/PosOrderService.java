package com.skysport.datn.service;

import com.skysport.datn.entity.Bill;
import com.skysport.datn.entity.BillDetail;
import com.skysport.datn.entity.DiscountCode;
import com.skysport.datn.entity.OrderStatusHistory;
import com.skysport.datn.entity.Payment;
import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.entity.Staff;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.enums.PosOrderStatus;
import com.skysport.datn.repository.BillDetailRepository;
import com.skysport.datn.repository.BillRepository;
import com.skysport.datn.repository.OrderStatusHistoryRepository;
import com.skysport.datn.repository.PaymentMethodRepository;
import com.skysport.datn.repository.ProductDetailRepository;
import com.skysport.datn.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.skysport.datn.exception.BusinessException;

/**
 * PosOrderService — xử lý nghiệp vụ bán hàng tại quầy (POS).
 *
 * Phase 2 fix:
 * - Chuyển từ @Autowired field injection sang constructor injection (@RequiredArgsConstructor).
 *   Lợi ích: dependency rõ ràng, class immutable hơn, dễ unit test,
 *   nhìn class là biết cần những gì thay vì phải scan toàn bộ field.
 *
 * Concurrency đã được fix ở Phase 1:
 * - pay() / cancel() / expire() dùng findByIdForUpdate() (PESSIMISTIC_WRITE)
 * - addItem() / setItemQuantity() / applyDiscount() / removeDiscount()
 *   dùng getEditableWaitingBill() → findByIdForUpdate() — lock toàn bộ edit operations
 */
@Service
@RequiredArgsConstructor
public class PosOrderService {

    private final BillRepository billRepository;
    private final BillDetailRepository billDetailRepository;
    private final ProductDetailRepository productDetailRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final StaffRepository staffRepository;
    private final DiscountCodeService discountCodeService;

    // LƯU ĐƠN POS
    public Bill save(Bill bill) {
        bill.setInvoiceType(2);
        return billRepository.save(bill);
    }

    // TÌM ĐƠN POS
    public Bill findById(Integer id) {
        return billRepository.findById(id).orElse(null);
    }

    // DANH SÁCH ĐƠN POS ĐANG CHỜ
    public List<Bill> findWaitingOrders() {
        return billRepository.findByInvoiceTypeAndPosStatusOrderByCreateDateDesc(2, PosOrderStatus.WAITING.getValue());
    }

    // DANH SÁCH ĐƠN POS ĐÃ HOÀN THÀNH
    public List<Bill> findCompletedOrders() {
        return billRepository.findByInvoiceTypeAndPosStatusOrderByUpdateDateDesc(2, PosOrderStatus.COMPLETED.getValue());
    }

    // CHUYỂN ĐƠN SANG WAITING
    @Transactional
    public Bill markWaiting(Bill bill) {
        if (bill == null) {
            throw new BusinessException("Đơn POS không tồn tại!");
        }
        bill.setInvoiceType(2);
        bill.setPosStatus(PosOrderStatus.WAITING.getValue());
        bill.setStatus(OrderStatus.PENDING.getValue());
        bill.setUpdateDate(LocalDateTime.now());
        return billRepository.save(bill);
    }

    // THÊM CHI TIẾT ĐƠN
    public BillDetail addDetail(BillDetail detail) {
        if (detail == null) {
            throw new BusinessException("Chi tiết đơn hàng không hợp lệ!");
        }
        return billDetailRepository.save(detail);
    }

    // THANH TOÁN ĐƠN POS
    @Transactional
    public Bill pay(Integer billId, Integer paymentMethodId, Integer staffId) {

        if (billId == null) {
            throw new BusinessException("Không xác định được đơn POS!");
        }

        // 1. Lock PESSIMISTIC_WRITE ngay từ đầu — tránh 2 request thanh toán cùng 1 đơn
        Bill bill = billRepository.findByIdForUpdate(billId).orElseThrow(() ->
                new BusinessException("Không tìm thấy đơn POS!"));

        // 2. Kiểm tra đúng đơn POS
        if (bill.getInvoiceType() == null || bill.getInvoiceType() != 2) {
            throw new BusinessException("Đây không phải đơn bán hàng tại quầy!");
        }

        // 3. Chỉ thanh toán đơn WAITING
        if (!PosOrderStatus.WAITING.matches(bill.getPosStatus())) {
            throw new BusinessException("Đơn này không còn ở trạng thái chờ thanh toán!");
        }

        // 4. Kiểm tra phương thức thanh toán
        if (paymentMethodId == null) {
            throw new BusinessException("Vui lòng chọn phương thức thanh toán!");
        }

        Payment payment = paymentMethodRepository.findById(paymentMethodId)
                .orElseThrow(() -> new BusinessException("Phương thức thanh toán không tồn tại!"));

        if (payment.getStatus() != null && payment.getStatus() != 1) {
            throw new BusinessException("Phương thức thanh toán hiện không hoạt động!");
        }

        // 5. Lấy chi tiết đơn
        List<BillDetail> details = billDetailRepository.findByBillId(bill.getId());
        if (details == null || details.isEmpty()) {
            throw new BusinessException("Đơn POS chưa có sản phẩm!");
        }

        // 6. Xác định nhân viên
        Staff staff = staffId != null ? staffRepository.findById(staffId).orElse(null) : null;

        // 7. Gom số lượng theo ProductDetail — xử lý trường hợp cùng variant nhiều dòng
        Map<Integer, Integer> quantityMap = new HashMap<>();
        for (BillDetail detail : details) {
            if (detail.getProductDetail() == null) {
                throw new BusinessException("Đơn hàng có sản phẩm không hợp lệ!");
            }
            if (detail.getQuantity() == null || detail.getQuantity() <= 0) {
                throw new BusinessException("Số lượng sản phẩm không hợp lệ!");
            }
            quantityMap.merge(detail.getProductDetail().getId(), detail.getQuantity(), Integer::sum);
        }

        // 8. Lock + kiểm tra + trừ tồn kho
        for (Map.Entry<Integer, Integer> entry : quantityMap.entrySet()) {
            Integer productDetailId   = entry.getKey();
            Integer requestedQuantity = entry.getValue();

            ProductDetail productDetail = productDetailRepository
                    .findByIdForUpdate(productDetailId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy biến thể sản phẩm!"));

            if (!productDetail.isSellable()) {
                String productName = productDetail.getProduct() != null
                        ? productDetail.getProduct().getName() : "Sản phẩm";
                throw new BusinessException("\"" + productName + "\" hiện không thể bán!");
            }

            Integer currentQuantity = productDetail.getQuantity();
            if (currentQuantity == null || currentQuantity < requestedQuantity) {
                String productName = productDetail.getProduct() != null
                        ? productDetail.getProduct().getName() : "Sản phẩm";
                throw new BusinessException(
                        "\"" + productName + "\" không đủ tồn kho! Còn "
                                + (currentQuantity != null ? currentQuantity : 0) + " sản phẩm.");
            }

            productDetail.setQuantity(currentQuantity - requestedQuantity);
            productDetailRepository.save(productDetail);
        }

        // 9–11. Cập nhật bill
        bill.setPaymentMethod(payment);
        bill.setPosStatus(PosOrderStatus.COMPLETED.getValue());
        bill.setStatus(OrderStatus.COMPLETED.getValue());
        bill.setUpdateDate(LocalDateTime.now());
        Bill savedBill = billRepository.save(bill);

        // 12. Ghi lịch sử trạng thái
        orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                .bill(savedBill)
                .staff(staff)
                .status(OrderStatus.COMPLETED.getValue())
                .note("Thanh toán đơn bán hàng tại quầy POS")
                .createdDate(LocalDateTime.now())
                .build());

        // 13. Tăng lượt sử dụng voucher
        if (savedBill.getDiscountCode() != null) {
            discountCodeService.incrementUsage(savedBill.getDiscountCode().getId());
        }

        return savedBill;
    }

    // HỦY ĐƠN POS
    @Transactional
    public Bill cancel(Integer billId) {
        if (billId == null) throw new BusinessException("Đơn POS không tồn tại!");

        Bill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new BusinessException("Đơn POS không tồn tại!"));

        if (!PosOrderStatus.WAITING.matches(bill.getPosStatus())) {
            throw new BusinessException("Chỉ có thể hủy đơn POS đang chờ!");
        }

        bill.setPosStatus(PosOrderStatus.CANCELLED.getValue());
        bill.setStatus(OrderStatus.CANCELLED.getValue());
        bill.setUpdateDate(LocalDateTime.now());
        bill = billRepository.save(bill);

        orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                .bill(bill)
                .status(OrderStatus.CANCELLED.getValue())
                .note("Hủy đơn bán hàng tại quầy POS")
                .createdDate(LocalDateTime.now())
                .build());

        return bill;
    }

    // ===== THÊM SẢN PHẨM VÀO ĐƠN POS ĐANG CHỜ =====
    @Transactional
    public Bill addItem(Integer billId, Integer productDetailId, Integer quantity) {
        Bill bill = getEditableWaitingBill(billId);

        if (quantity == null || quantity <= 0) {
            throw new BusinessException("Số lượng phải lớn hơn 0!");
        }

        ProductDetail pd = productDetailRepository.findById(productDetailId)
                .orElseThrow(() -> new BusinessException("Sản phẩm không tồn tại!"));

        if (!pd.isSellable()) {
            throw new BusinessException("\"" + pd.getProduct().getName() + "\" hiện không thể bán!");
        }

        List<BillDetail> details = billDetailRepository.findByBillId(billId);
        BillDetail existing = details.stream()
                .filter(d -> d.getProductDetail() != null
                        && d.getProductDetail().getId().equals(productDetailId))
                .findFirst().orElse(null);

        int currentQtyInBill = existing != null && existing.getQuantity() != null ? existing.getQuantity() : 0;
        int newQtyInBill     = currentQtyInBill + quantity;

        if (pd.getQuantity() == null || pd.getQuantity() < newQtyInBill) {
            throw new BusinessException("\"" + pd.getProduct().getName() + "\" chỉ còn "
                    + (pd.getQuantity() != null ? pd.getQuantity() : 0) + " cái!");
        }

        if (existing != null) {
            existing.setQuantity(newQtyInBill);
            existing.setMomentPrice(pd.getFinalPrice());
            billDetailRepository.save(existing);
        } else {
            BillDetail detail = new BillDetail();
            detail.setBill(bill);
            detail.setProductDetail(pd);
            detail.setMomentPrice(pd.getFinalPrice());
            detail.setQuantity(quantity);
            billDetailRepository.save(detail);
        }

        return recalcTotals(bill);
    }

    // ===== SỬA SỐ LƯỢNG 1 DÒNG TRONG ĐƠN POS ĐANG CHỜ =====
    @Transactional
    public Bill setItemQuantity(Integer billId, Integer billDetailId, Integer quantity) {
        Bill bill = getEditableWaitingBill(billId);

        BillDetail detail = billDetailRepository.findById(billDetailId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy sản phẩm trong đơn!"));

        if (detail.getBill() == null || !detail.getBill().getId().equals(billId)) {
            throw new BusinessException("Sản phẩm không thuộc đơn này!");
        }

        if (quantity == null || quantity <= 0) {
            billDetailRepository.delete(detail);
        } else {
            ProductDetail pd = detail.getProductDetail();
            if (pd == null || !pd.isSellable()) {
                throw new BusinessException("Sản phẩm hiện không thể bán!");
            }
            if (pd.getQuantity() == null || pd.getQuantity() < quantity) {
                throw new BusinessException("\"" + pd.getProduct().getName() + "\" chỉ còn "
                        + (pd.getQuantity() != null ? pd.getQuantity() : 0) + " cái!");
            }
            detail.setQuantity(quantity);
            billDetailRepository.save(detail);
        }

        List<BillDetail> remaining = billDetailRepository.findByBillId(billId);
        if (remaining.isEmpty()) {
            throw new BusinessException("Đơn phải còn ít nhất 1 sản phẩm! Muốn hủy cả đơn, dùng nút \"Hủy đơn\".");
        }

        return recalcTotals(bill);
    }

    // ===== ÁP MÃ GIẢM GIÁ CHO ĐƠN POS ĐANG CHỜ =====
    @Transactional
    public Bill applyDiscount(Integer billId, String code) {
        Bill bill = getEditableWaitingBill(billId);

        if (code == null || code.isBlank()) {
            throw new BusinessException("Vui lòng nhập mã giảm giá!");
        }

        double subtotal = 0;
        for (BillDetail d : billDetailRepository.findByBillId(billId)) {
            double price = d.getMomentPrice() != null ? d.getMomentPrice().doubleValue() : 0;
            int    qty   = d.getQuantity()    != null ? d.getQuantity()    : 0;
            subtotal += price * qty;
        }

        Integer customerId = bill.getCustomer() != null ? bill.getCustomer().getId() : null;
        String validation  = discountCodeService.validate(code, subtotal, customerId);

        if (!"OK".equals(validation)) {
            throw new BusinessException(validation);
        }

        bill.setDiscountCode(discountCodeService.findByCode(code));
        return recalcTotals(bill);
    }

    // ===== HỦY MÃ GIẢM GIÁ ĐANG ÁP CHO ĐƠN POS ĐANG CHỜ =====
    @Transactional
    public Bill removeDiscount(Integer billId) {
        Bill bill = getEditableWaitingBill(billId);
        bill.setDiscountCode(null);
        return recalcTotals(bill);
    }

    /**
     * Lấy đơn POS WAITING với PESSIMISTIC_WRITE lock.
     * Dùng cho toàn bộ edit operations: addItem, setItemQuantity, applyDiscount, removeDiscount.
     * Tránh lost update khi 2 nhân viên cùng sửa 1 đơn chờ.
     */
    private Bill getEditableWaitingBill(Integer billId) {
        if (billId == null) throw new BusinessException("Không xác định được đơn POS!");

        Bill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn POS!"));

        if (bill.getInvoiceType() == null || bill.getInvoiceType() != 2) {
            throw new BusinessException("Đây không phải đơn bán hàng tại quầy!");
        }
        if (!PosOrderStatus.WAITING.matches(bill.getPosStatus())) {
            throw new BusinessException("Đơn này không còn ở trạng thái chờ, không thể sửa!");
        }

        return bill;
    }

    private Bill recalcTotals(Bill bill) {
        List<BillDetail> details = billDetailRepository.findByBillId(bill.getId());
        double subtotal = 0;

        for (BillDetail d : details) {
            double price = d.getMomentPrice() != null ? d.getMomentPrice().doubleValue() : 0;
            int    qty   = d.getQuantity()    != null ? d.getQuantity()    : 0;
            subtotal += price * qty;
        }

        double discountAmount = 0;
        if (bill.getDiscountCode() != null) {
            discountAmount = discountCodeService.calculateDiscount(bill.getDiscountCode(), subtotal);
            if (discountAmount > subtotal) discountAmount = subtotal;
        }

        bill.setSubtotal((float) subtotal);
        bill.setPromotionPrice((float) discountAmount);
        bill.setAmount((float) (subtotal - discountAmount));
        bill.setUpdateDate(LocalDateTime.now());
        return billRepository.save(bill);
    }

    // HẾT HẠN ĐƠN POS
    @Transactional
    public Bill expire(Integer billId) {
        if (billId == null) throw new BusinessException("Đơn POS không tồn tại!");

        Bill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new BusinessException("Đơn POS không tồn tại!"));

        if (!PosOrderStatus.WAITING.matches(bill.getPosStatus())) {
            throw new BusinessException("Chỉ có thể hết hạn đơn POS đang chờ!");
        }

        bill.setPosStatus(PosOrderStatus.EXPIRED.getValue());
        bill.setStatus(OrderStatus.CANCELLED.getValue());
        bill.setUpdateDate(LocalDateTime.now());
        bill = billRepository.save(bill);

        orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                .bill(bill)
                .status(OrderStatus.CANCELLED.getValue())
                .note("Đơn bán hàng tại quầy POS hết hạn chờ thanh toán")
                .createdDate(LocalDateTime.now())
                .build());

        return bill;
    }

    /**
     * Tự động hết hạn các đơn POS còn WAITING từ hôm trước trở về trước
     * (không dùng timeout theo phút như banking — nhân viên có thể cần
     * thời gian trao đổi với khách trong ngày, nhưng đơn để qua ngày hôm
     * sau thì chắc chắn khách đã bỏ đi). Gọi định kỳ bởi PendingOrderCleanupJob.
     * Tái dùng expire() từng đơn để giữ đúng lock + lịch sử đã có, không viết lại.
     *
     * @return số đơn POS đã bị hết hạn tự động.
     */
    @Transactional
    public int expireOverdueWaitingOrders() {
        // Đúng — hủy sau 1 giờ
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        List<Bill> candidates = billRepository.findWaitingPosBillsCreatedBefore(cutoff);

        int expiredCount = 0;
        for (Bill bill : candidates) {
            try {
                expire(bill.getId());
                expiredCount++;
            } catch (BusinessException e) {
                // Đơn có thể đã được thanh toán/hủy bởi luồng khác giữa lúc query và lúc expire() lock lại — bỏ qua.
            }
        }
        return expiredCount;
    }
}