package com.skysport.datn.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ShippingFeeRequest {

    @NotNull(message = "Vui lòng chọn tỉnh/thành phố nhận hàng")
    private Integer provinceId;

    @NotNull(message = "Không xác định được giá trị đơn hàng")
    @DecimalMin(value = "0.00", message = "Giá trị đơn hàng không hợp lệ")
    private BigDecimal subtotal;
}
