package com.skysport.datn.service;

import com.skysport.datn.entity.*;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class BillService {

    private static final Logger log = LoggerFactory.getLogger(BillService.class);

    /** Đơn PENDING chuyển khoản quá thời gian này (phút) sẽ bị tự động hủy. */
    private static final long PENDING_BANKING_TIMEOUT_MINUTES = 15;

    private final ProductDetailRepository productDetailRepository;
    private final DiscountCodeService discountCodeService;
    private final BillRepository billRepository;
    private final BillDetailRepository billDetailRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final StaffRepository staffRepository;

    public List<Bill> findAll() {
        return billRepository.findAllByOrderByCreateDateDesc();
    }

    public List<Bill> findByStatus(Integer status) {
        return billRepository.findByStatusOrderByCreateDateDesc(status);
    }

    public Page<Bill> findAllPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return billRepository.findAllByOrderByCreateDateDesc(pageable);
    }

    public Page<Bill> findByStatusPaged(Integer status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return billRepository.findByStatusOrderByCreateDateDesc(status, pageable);
    }

    public Bill findById(Integer id) {
        return billRepository.findById(id).orElse(null);
    }

    public List<BillDetail> findDetailsByBillId(Integer billId) {
        return billDetailRepository.findByBillId(billId);
    }

    public List<OrderStatusHistory> findHistoryByBillId(Integer billId) {
        return orderStatusHistoryRepository.findByBillIdOrderByCreatedDateAsc(billId);
    }

    /**
     * Đơn có phải thanh toán qua "Chuyển khoản / VNPay" hay không, dựa vào
     * tên Payment. Dùng chung cho cả logic hoàn voucher (updateStatus) và
     * job tự hủy đơn bỏ dở (autoCancelExpiredBankingOrders).
     */
    private boolean isBankingPayment(Bill bill) {
        if (bill.getPaymentMethod() == null || bill.getPaymentMethod().getName() == null) {
            return false;
        }
        String paymentName = bill.getPaymentMethod().getName().toUpperCase();
        return paymentName.contains("CHUYỂN")
                || paymentName.contains("BANK")
                || paymentName.contains("KHOẢN")
                || paymentName.contains("VNPAY");
    }

    /**
     * Chuyển trạng thái đơn hàng theo state machine:
     * PENDING → CONFIRMED | CANCELLED
     * CONFIRMED → SHIPPING | CANCELLED
     * SHIPPING → COMPLETED   (RETURNING đi qua BillReturnRequest)
     */
    @Transactional
    public boolean updateStatus(Integer billId, Integer newStatus, String note, Account account) {
        Bill bill = findById(billId);
        if (bill == null) return false;

        OrderStatus current = OrderStatus.of(bill.getStatus());
        OrderStatus next    = OrderStatus.of(newStatus);
        if (current == null || next == null) return false;

        boolean valid = switch (current) {
            case PENDING   -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.SHIPPING  || next == OrderStatus.CANCELLED;
            case SHIPPING  -> next == OrderStatus.COMPLETED;
            default        -> false;
        };

        if (!valid) return false;

        if (next == OrderStatus.CANCELLED) {

            // Hoàn lượt voucher nếu voucher đã được tính trước đó
            if (bill.getDiscountCode() != null) {

                boolean shouldRestoreVoucher = false;

                // Đơn CONFIRMED chắc chắn đã được tính voucher
                // (COD đã tính khi đặt hàng, Banking đã tính khi thanh toán)
                if (current == OrderStatus.CONFIRMED) {
                    shouldRestoreVoucher = true;
                }

                // Đơn PENDING:
                // - COD: đã tính voucher khi đặt hàng -> phải hoàn
                // - Banking: chưa thanh toán -> chưa tính voucher -> không hoàn
                if (current == OrderStatus.PENDING && !isBankingPayment(bill)) {
                    shouldRestoreVoucher = true;
                }

                if (shouldRestoreVoucher) {
                    discountCodeService.decrementUsage(
                            bill.getDiscountCode().getId()
                    );
                }
            }

            restockBillItems(bill);
        }

        bill.setStatus(next.getValue());
        bill.setUpdateDate(LocalDateTime.now());
        billRepository.save(bill);

        Staff staff = null;
        if (account != null) {
            staff = staffRepository.findByAccountId(account.getId());
        }

        OrderStatusHistory history = new OrderStatusHistory();
        history.setBill(bill);
        history.setStatus(next.getValue());
        history.setNote(note != null && !note.isBlank() ? note : next.getLabel());
        history.setCreatedDate(LocalDateTime.now());
        history.setStaff(staff);
        orderStatusHistoryRepository.save(history);

        return true;
    }

    /**
     * Tự động hủy các đơn online thanh toán "Chuyển khoản/VNPay" bị khách
     * bỏ dở (không hoàn tất bước mock-vnpay-pay), quá
     * PENDING_BANKING_TIMEOUT_MINUTES phút vẫn ở PENDING.
     * Được gọi định kỳ bởi PendingOrderCleanupJob.
     * Tận dụng lại updateStatus() để đảm bảo hoàn kho + hoàn voucher
     * (nếu có) chạy đúng logic hiện có, không viết lại.
     *
     * @return số đơn đã bị hủy tự động, để job log lại.
     */
    @Transactional
    public int autoCancelExpiredBankingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(PENDING_BANKING_TIMEOUT_MINUTES);
        List<Bill> candidates = billRepository.findPendingOnlineBillsCreatedBefore(cutoff);

        int cancelledCount = 0;
        for (Bill bill : candidates) {
            if (!isBankingPayment(bill)) {
                // Đơn COD PENDING quá hạn là chuyện bình thường (chờ nhân viên xác nhận),
                // không tự hủy.
                continue;
            }
            boolean ok = updateStatus(
                    bill.getId(),
                    OrderStatus.CANCELLED.getValue(),
                    "Tự động hủy: quá " + PENDING_BANKING_TIMEOUT_MINUTES
                            + " phút không hoàn tất thanh toán chuyển khoản",
                    null
            );
            if (ok) {
                cancelledCount++;
                log.info("Auto-cancelled abandoned banking order billId={} code={}", bill.getId(), bill.getCode());
            }
        }
        return cancelledCount;
    }

    private void restockBillItems(Bill bill) {
        List<BillDetail> details = billDetailRepository.findByBillId(bill.getId());
        for (BillDetail detail : details) {
            if (detail.getProductDetail() == null || detail.getQuantity() == null) continue;
            ProductDetail pd = productDetailRepository.findById(detail.getProductDetail().getId()).orElse(null);
            if (pd != null) {
                pd.setQuantity((pd.getQuantity() != null ? pd.getQuantity() : 0) + detail.getQuantity());
                productDetailRepository.save(pd);
            }
        }
    }

    public String getStatusText(Integer status) {
        OrderStatus os = OrderStatus.of(status);
        return os != null ? os.getLabel() : "Không xác định";
    }
}