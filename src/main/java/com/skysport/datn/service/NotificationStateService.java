package com.skysport.datn.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Lưu mốc thời gian "admin đã xem thông báo khách hàng mới gần nhất" ngay trong bộ nhớ
 * (không cần thêm cột/bảng trong DB).
 * Nhược điểm: nếu restart server thì mốc này reset lại (coi như tất cả khách hàng hiện có
 * là "đã xem" tính từ lúc khởi động, chỉ khách đăng ký sau đó mới tính là mới).
 */
@Service
public class NotificationStateService {

    // Mặc định = thời điểm server khởi động -> khách hàng có sẵn trước đó không bị tính là "mới"
    private volatile LocalDateTime customerLastViewedAt = LocalDateTime.now();

    public LocalDateTime getCustomerLastViewedAt() {
        return customerLastViewedAt;
    }

    public void markCustomersViewedNow() {
        this.customerLastViewedAt = LocalDateTime.now();
    }
}