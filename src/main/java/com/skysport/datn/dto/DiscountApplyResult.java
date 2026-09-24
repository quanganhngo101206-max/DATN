package com.skysport.datn.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DiscountApplyResult {
    boolean success;
    String message;
    long discountAmount;
    long finalTotal;
    long shipping;
}