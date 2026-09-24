package com.skysport.datn.util;

import com.skysport.datn.entity.Payment;

/**
 * Nhận diện phương thức thanh toán "Chuyển khoản / VNPay" dựa vào tên Payment.
 * <p>
 * Tách ra thành class tĩnh dùng chung (thay vì để private method riêng trong
 * BillService) vì logic này giờ cần dùng ở cả DiscountCodeService (khi kiểm
 * tra khách đã dùng voucher hay chưa) lẫn BillService (hoàn voucher/tồn kho).
 * Nếu để DiscountCodeService inject thẳng BillService sẽ tạo dependency vòng
 * (BillService đã inject DiscountCodeService), nên dùng static util là cách
 * an toàn nhất.
 */
public final class PaymentMethodUtil {

    private PaymentMethodUtil() {
    }

    public static boolean isBanking(Payment payment) {
        if (payment == null || payment.getName() == null) {
            return false;
        }
        String paymentName = payment.getName().toUpperCase();
        return paymentName.contains("CHUYỂN")
                || paymentName.contains("BANK")
                || paymentName.contains("KHOẢN")
                || paymentName.contains("VNPAY");
    }
}