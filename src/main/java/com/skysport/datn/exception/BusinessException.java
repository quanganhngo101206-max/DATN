package com.skysport.datn.exception;

/**
 * Lỗi nghiệp vụ (validation, trạng thái không hợp lệ, dữ liệu không đủ điều kiện...).
 * Phân biệt với lỗi hệ thống (DB down, bug, NPE...) vốn nên là Exception/RuntimeException thường.
 * Message của exception này được coi là an toàn để hiển thị trực tiếp cho người dùng.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}