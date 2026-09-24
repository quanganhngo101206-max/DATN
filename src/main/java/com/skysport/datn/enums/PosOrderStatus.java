package com.skysport.datn.enums;

/**
 * Trạng thái đơn POS (Bill.posStatus)
 * Tách riêng với Bill.status của đơn online.
 */
public enum PosOrderStatus {

    WAITING(1, "Chờ thanh toán"),
    COMPLETED(2, "Hoàn thành"),
    CANCELLED(3, "Đã hủy"),
    EXPIRED(4, "Hết hạn");

    private final int value;
    private final String label;

    PosOrderStatus(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() { return value; }
    public String getLabel() { return label; }

    /** Chuyển int → enum, trả null nếu không khớp */
    public static PosOrderStatus of(Integer value) {
        if (value == null) return null;
        for (PosOrderStatus s : values()) {
            if (s.value == value) return s;
        }
        return null;
    }

    /** Tiện dùng khi cần so sánh */
    public boolean matches(Integer value) {
        return this.value == (value == null ? -1 : value);
    }
}