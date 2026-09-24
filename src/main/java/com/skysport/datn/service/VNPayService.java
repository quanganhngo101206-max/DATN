package com.skysport.datn.service;

import com.skysport.datn.config.VNPayConfig;
import com.skysport.datn.entity.Bill;
import com.skysport.datn.repository.BillRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VNPayService {

    private static final Logger log = LoggerFactory.getLogger(VNPayService.class);

    private final VNPayConfig config;
    private final BillRepository billRepository;

    /** Kết quả build URL thanh toán: URL để redirect + mã txnRef vừa sinh (đã lưu vào Bill). */
    public record PaymentUrlResult(String paymentUrl, String txnRef) {}

    /** Kết quả xác thực Return URL / IPN. */
    public enum VerifyStatus { SUCCESS, FAILED, INVALID_SIGNATURE, ORDER_NOT_FOUND, AMOUNT_MISMATCH }

    public record VerifyResult(VerifyStatus status, Bill bill, String transactionNo, String payDate, String bankCode) {}

    /**
     * Tạo URL thanh toán sandbox VNPay cho một Bill cụ thể.
     * vnp_TxnRef được sinh mới mỗi lần gọi và lưu lại vào Bill để đối soát
     * khi VNPay gọi về (khách có thể bấm "thanh toán lại" nhiều lần).
     *
     * amount: số tiền VND (KHÔNG nhân 100 ở đây, hàm tự nhân theo yêu cầu VNPay).
     */
    public PaymentUrlResult createPaymentUrl(Bill bill, long amount, String orderInfo, HttpServletRequest request) {
        String vnp_TxnRef = "HD" + bill.getId() + "T" + VNPayConfig.getRandomNumber(6);

        String vnp_IpAddr;
        try {
            vnp_IpAddr = getIpAddress(request);
        } catch (Exception e) {
            vnp_IpAddr = "127.0.0.1";
        }

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", config.getTmnCode());
        vnp_Params.put("vnp_Amount", String.valueOf(amount * 100)); // VNPay yêu cầu amount * 100, dùng long tránh tràn số
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", orderInfo);
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");

        String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        vnp_Params.put("vnp_ReturnUrl", baseUrl + config.getReturnUrl());
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        vnp_Params.put("vnp_CreateDate", formatter.format(cld.getTime()));

        cld.add(Calendar.MINUTE, 15);
        vnp_Params.put("vnp_ExpireDate", formatter.format(cld.getTime()));

        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                try {
                    hashData.append(fieldName).append('=')
                            .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII)).append('=')
                            .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                } catch (Exception e) {
                    log.error("Lỗi encode VNPay param: {}", fieldName, e);
                }
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String vnp_SecureHash = hmacSHA512(config.getHashSecret(), hashData.toString());
        String paymentUrl = config.getPayUrl() + "?" + query + "&vnp_SecureHash=" + vnp_SecureHash;

        // Lưu txnRef vào Bill để Return URL / IPN đối soát ngược lại đúng hóa đơn
        bill.setVnpTxnRef(vnp_TxnRef);
        billRepository.save(bill);

        System.out.println("===== VNPAY DEBUG =====");
        System.out.println("TmnCode = " + config.getTmnCode());
        System.out.println("Payment URL = " + paymentUrl);
        System.out.println("=======================");

        return new PaymentUrlResult(paymentUrl, vnp_TxnRef);
    }

    /**
     * Dùng chung cho cả Return URL và IPN: verify chữ ký, tìm Bill theo vnp_TxnRef,
     * đối chiếu số tiền để chống giả mạo tham số, rồi trả kết quả cho controller quyết định.
     * KHÔNG tự ý đổi trạng thái Bill ở đây — service chỉ xác thực dữ liệu.
     */
    public VerifyResult verify(HttpServletRequest request) {
        Map<String, String> fields = new HashMap<>();
        for (Enumeration<String> params = request.getParameterNames(); params.hasMoreElements(); ) {
            String rawName = params.nextElement();
            String fieldValue = request.getParameter(rawName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                fields.put(rawName, fieldValue);
            }
        }

        String vnp_SecureHash = fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");

        String signValue = hashAllFields(fields);
        if (vnp_SecureHash == null || !signValue.equals(vnp_SecureHash)) {
            return new VerifyResult(VerifyStatus.INVALID_SIGNATURE, null, null, null, null);
        }

        String txnRef = fields.get("vnp_TxnRef");
        Bill bill = txnRef == null ? null : billRepository.findByVnpTxnRef(txnRef).orElse(null);
        if (bill == null) {
            return new VerifyResult(VerifyStatus.ORDER_NOT_FOUND, null, null, null, null);
        }

        // Đối chiếu số tiền VNPay báo về với số tiền thực của hóa đơn, tránh tampering ở query string
        try {
            long vnpAmount = Long.parseLong(fields.get("vnp_Amount")) / 100;
            long billAmount = Math.round(bill.getAmount());
            if (vnpAmount != billAmount) {
                return new VerifyResult(VerifyStatus.AMOUNT_MISMATCH, bill, null, null, null);
            }
        } catch (Exception e) {
            return new VerifyResult(VerifyStatus.AMOUNT_MISMATCH, bill, null, null, null);
        }

        String transactionNo = fields.get("vnp_TransactionNo");
        String payDate = fields.get("vnp_PayDate");
        String bankCode = fields.get("vnp_BankCode");

        boolean paySuccess = "00".equals(fields.get("vnp_TransactionStatus"))
                && "00".equals(fields.get("vnp_ResponseCode"));

        return new VerifyResult(
                paySuccess ? VerifyStatus.SUCCESS : VerifyStatus.FAILED,
                bill, transactionNo, payDate, bankCode
        );
    }

    public String hashAllFields(Map<String, String> fields) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder sb = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                sb.append(fieldName).append("=").append(fieldValue);
            }
            if (itr.hasNext()) {
                sb.append("&");
            }
        }
        return hmacSHA512(config.getHashSecret(), sb.toString());
    }

    public static String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) {
                throw new NullPointerException();
            }
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    public static String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-FORWARDED-FOR");
        if (ip == null) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}