package com.skysport.datn.enums;

/**
 * Trạng thái đơn hàng (Bill.status)
 * Thay thế cho magic number 1–7 rải rác trong code.
 */
public enum OrderStatus {

    PENDING(1, "Chờ xác nhận"),
    CONFIRMED(2, "Đã xác nhận"),
    SHIPPING(3, "Đang giao"),
    DELIVERED(4, "Đã giao"),
    CANCELLED(5, "Đã hủy"),
    RETURNING(6, "Trả hàng"),
    COMPLETED(7, "Hoàn thành");

    private final int value;
    private final String label;

    OrderStatus(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() { return value; }
    public String getLabel() { return label; }

    /** Chuyển int → enum, trả null nếu không khớp */
    public static OrderStatus of(Integer value) {
        if (value == null) return null;
        for (OrderStatus s : values()) {
            if (s.value == value) return s;
        }
        return null;
    }

    /** Tiện dùng khi cần so sánh: bill.getStatus() == OrderStatus.COMPLETED.getValue() */
    public boolean matches(Integer value) {
        return this.value == (value == null ? -1 : value);
    }
}