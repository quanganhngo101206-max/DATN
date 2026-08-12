package com.skysport.datn.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class ShippingFeeResponse {

    Integer provinceId;
    String provinceName;
    String zone;
    BigDecimal subtotal;
    BigDecimal baseFee;
    BigDecimal shippingFee;
    BigDecimal freeShippingThreshold;
    boolean freeShipping;
    String message;
}
