package com.skysport.datn.enums;

/** Trạng thái yêu cầu trả hàng (BillReturnRequest.status) */
public enum ReturnRequestStatus {
    PENDING(1, "Chờ duyệt"),
    APPROVED(2, "Đã duyệt"),
    REJECTED(3, "Từ chối");

    private final int value;
    private final String label;

    ReturnRequestStatus(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() { return value; }
    public String getLabel() { return label; }

    public boolean matches(Integer v) {
        return this.value == (v == null ? -1 : v);
    }
}