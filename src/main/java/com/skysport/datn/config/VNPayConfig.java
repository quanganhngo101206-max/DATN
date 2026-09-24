package com.skysport.datn.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;

import java.util.Random;

/**
 * Cấu hình VNPay đọc từ application*.properties (không hardcode secret trong code).
 * hash-secret KHÔNG có giá trị mặc định: phải set biến môi trường VNP_HASH_SECRET
 * (thiếu thì app báo lỗi ngay khi khởi động). TmnCode/URL sandbox có default vì không phải bí mật.
 */
@Configuration
@Getter
public class VNPayConfig {

    @Value("${vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String payUrl;

    @Value("${vnpay.tmn-code:50CQRUTK}")
    private String tmnCode;

    @Value("${vnpay.hash-secret}")
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