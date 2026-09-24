package com.skysport.datn.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;

import java.util.Random;

/**
 * Cấu hình VNPay đọc từ application*.properties (không hardcode secret trong code).
 * Giá trị mặc định trong dấu ':' chỉ để chạy demo nhanh — PHẢI thay bằng
 * TmnCode/HashSecret của tài khoản merchant sandbox riêng của bạn
 * (đăng ký tại https://sandbox.vnpayment.vn) trước khi nộp báo cáo / deploy thật,
 * vì cặp key mặc định là key demo public, ai cũng dùng được.
 */
@Configuration
@Getter
public class VNPayConfig {

    @Value("${vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String payUrl;

    @Value("${vnpay.tmn-code:50CQRUTK}")
    private String tmnCode;

    @Value("${vnpay.hash-secret:7V88248X23T0M51V234479K0G98T16X2}")
    private String hashSecret;

    @Value("${vnpay.api-url:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}")
    private String apiUrl;

    /** Đường dẫn (relative) VNPay sẽ redirect trình duyệt khách về sau khi thanh toán. */
    @Value("${vnpay.return-url:/vnpay-return}")
    private String returnUrl;

    public static String getRandomNumber(int len) {
        Random rnd = new Random();
        String chars = "0123456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
