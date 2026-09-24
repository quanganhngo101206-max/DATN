package com.skysport.datn.scheduler;

import com.skysport.datn.service.BillService;
import com.skysport.datn.service.PosOrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Quét định kỳ để tự hủy các đơn online thanh toán "Chuyển khoản/VNPay"
 * bị khách bỏ dở (không hoàn tất bước xác thực OTP ở trang mock-vnpay),
 * tránh giữ tồn kho vĩnh viễn cho các đơn không bao giờ được thanh toán.
 *
 * Đơn COD ở trạng thái PENDING KHÔNG bị job này đụng tới — COD PENDING
 * là trạng thái chờ nhân viên xác nhận bình thường, không phải lỗi.
 *
 * Cũng xử lý luôn đơn POS (bán tại quầy) còn WAITING quá ngày — đây là
 * lifecycle riêng của POS (posStatus), tách hẳn khỏi state machine online
 * (Bill.status) nên dùng PosOrderService.expireOverdueWaitingOrders(),
 * không đi qua BillService.updateStatus().
 */
@Component
@RequiredArgsConstructor
public class PendingOrderCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderCleanupJob.class);

    private final BillService billService;
    private final PosOrderService posOrderService;

    /** Chạy mỗi 2 phút (120 000 ms). */
    @Scheduled(fixedRate = 120_000)
    public void cleanupExpiredBankingOrders() {
        int cancelled = billService.autoCancelExpiredBankingOrders();
        if (cancelled > 0) {
            log.info("PendingOrderCleanupJob: đã tự hủy {} đơn chuyển khoản bị bỏ dở", cancelled);
        }

        int expiredPos = posOrderService.expireOverdueWaitingOrders();
        if (expiredPos > 0) {
            log.info("PendingOrderCleanupJob: đã tự hết hạn {} đơn POS còn chờ từ hôm trước", expiredPos);
        }
    }
}