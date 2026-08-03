package com.skysport.datn.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CheckoutRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự")
    private String fullName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(0|\\+84)[3-9]\\d{8}$", message = "Số điện thoại không hợp lệ")
    private String phoneNumber;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;

    @NotBlank(message = "Địa chỉ giao hàng không được để trống")
    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String note;

    @NotBlank(message = "Vui lòng chọn phương thức thanh toán")
    private String paymentMethod;

    private String discountCode;

    private Integer provinceId;   // ID tỉnh/thành phố được chọn
    private Integer wardId;       // ID phường/xã được chọn
    // Đã bỏ districtId vì Ward map thẳng lên Province
}