package com.skysport.datn.service;

import com.skysport.datn.entity.*;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.repository.*;
import com.skysport.datn.util.PaymentMethodUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    @Value("${app.qr-payment.expire-minutes:15}")
    private long pendingBankingTimeoutMinutes;

    private final ProductDetailRepository productDetailRepository;
    private final DiscountCodeService discountCodeService;
    private final BillRepository billRepository;
    private final BillDetailRepository billDetailRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final StaffRepository staffRepository;

    private static final int ONLINE_INVOICE_TYPE = 1;

    public List<Bill> findAll() {
        return billRepository.findAllByInvoiceTypeOrderByCreateDateDesc(ONLINE_INVOICE_TYPE);
    }

    public List<Bill> findByStatus(Integer status) {
        return billRepository.findByStatusAndInvoiceTypeOrderByCreateDateDesc(status, ONLINE_INVOICE_TYPE);
    }

    public Page<Bill> findAllPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return billRepository.findAllByInvoiceTypeOrderByCreateDateDesc(ONLINE_INVOICE_TYPE, pageable);
    }

    public Page<Bill> findByStatusPaged(Integer status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return billRepository.findByStatusAndInvoiceTypeOrderByCreateDateDesc(status, ONLINE_INVOICE_TYPE, pageable);
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
        return PaymentMethodUtil.isBanking(bill.getPaymentMethod());
    }

    /**
     * Chuyển trạng thái đơn hàng theo state machine:
     * PENDING → CONFIRMED | CANCELLED
     * CONFIRMED → SHIPPING | CANCELLED
     * SHIPPING → COMPLETED   (RETURNING đi qua BillReturnRequest)
     * <p>
     * Khóa PESSIMISTIC_WRITE trên Bill trước khi đọc trạng thái: tránh trường
     * hợp 2 luồng cùng đổi trạng thái 1 đơn cùng lúc (vd. job tự hủy đơn quá
     * hạn chạy đúng lúc /vnpay-return hoặc /vnpay-ipn cũng đang xử lý đơn đó)
     * dẫn tới hoàn kho/hoàn voucher 2 lần hoặc ghi đè trạng thái sai.
     * <p>
     * REQUIRES_NEW: mỗi lần gọi chạy trong transaction riêng — quan trọng khi
     * autoCancelExpiredBankingOrders() gọi method này trong vòng lặp. Nếu dùng
     * REQUIRED (mặc định), 1 đơn lỗi sẽ rollback toàn bộ batch. Với REQUIRES_NEW,
     * các đơn thành công được commit độc lập, đơn lỗi chỉ rollback chính nó.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean updateStatus(Integer billId, Integer newStatus, String note, Account account) {
        if (billId == null) return false;
        Bill bill = billRepository.findByIdForUpdate(billId).orElse(null);
        if (bill == null) return false;

        // POS (bán tại quầy) có lifecycle riêng theo Bill.posStatus (WAITING/COMPLETED/
        // CANCELLED/EXPIRED), xử lý bởi PosOrderService — không đi qua state machine online
        // này. Nếu không chặn ở đây, đơn POS (cũng có status=PENDING lúc tạo) có thể bị
        // "Xác nhận"/"Hủy" nhầm từ màn Hóa đơn, làm Bill.status và Bill.posStatus lệch nhau.
        if (bill.getInvoiceType() != null && bill.getInvoiceType() == 2) {
            return false;
        }

        OrderStatus current = OrderStatus.of(bill.getStatus());
        OrderStatus next = OrderStatus.of(newStatus);
        if (current == null || next == null) return false;

        boolean valid = switch (current) {
            case PENDING -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.SHIPPING || next == OrderStatus.CANCELLED;
            case SHIPPING -> next == OrderStatus.COMPLETED;
            default -> false;
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
     * Không đánh @Transactional ở đây — updateStatus() đã dùng REQUIRES_NEW,
     * mỗi đơn tự quản lý transaction riêng. Nếu method này cũng có @Transactional,
     * SQL Server có thể deadlock: outer tx giữ shared lock trên bill từ SELECT,
     * inner tx (REQUIRES_NEW) muốn UPDLOCK trên cùng row đó.
     *
     * @return số đơn đã bị hủy tự động, để job log lại.
     */
    public int autoCancelExpiredBankingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(pendingBankingTimeoutMinutes);
        List<Bill> candidates = billRepository.findPendingOnlineBillsCreatedBefore(cutoff);

        int cancelledCount = 0;
        for (Bill bill : candidates) {
            if (!isBankingPayment(bill)) {
                // Đơn COD PENDING quá hạn là chuyện bình thường (chờ nhân viên xác nhận),
                // không tự hủy.
                continue;
            }
            try {
                boolean ok = updateStatus(
                        bill.getId(),
                        OrderStatus.CANCELLED.getValue(),
                        "Tự động hủy: quá " + pendingBankingTimeoutMinutes
                                + " phút không hoàn tất thanh toán chuyển khoản",
                        null
                );
                if (ok) {
                    cancelledCount++;
                    log.info("Auto-cancelled abandoned banking order billId={} code={}", bill.getId(), bill.getCode());
                }
            } catch (Exception e) {
                // 1 đơn lỗi không dừng cả batch — log lại để theo dõi
                log.error("Failed to auto-cancel billId={}: {}", bill.getId(), e.getMessage());
            }
        }
        return cancelledCount;
    }

    private void restockBillItems(Bill bill) {
        List<BillDetail> details = billDetailRepository.findByBillId(bill.getId());
        for (BillDetail detail : details) {
            if (detail.getProductDetail() == null || detail.getQuantity() == null) continue;
            // Khóa PESSIMISTIC_WRITE giống lúc trừ kho ở checkout — restock cũng là ghi
            // vào ProductDetail.quantity, không khóa thì có thể lost update nếu trùng lúc
            // có thao tác khác (nhập kho, đơn khác trừ/hoàn kho) trên cùng productDetail.
            ProductDetail pd = productDetailRepository.findByIdForUpdate(detail.getProductDetail().getId()).orElse(null);
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