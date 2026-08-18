package com.skysport.datn.controller.admin;

import com.skysport.datn.dto.ShippingFeeRequest;
import com.skysport.datn.dto.ShippingFeeResponse;
import com.skysport.datn.service.ShippingFeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingFeeController {

    private final ShippingFeeService shippingFeeService;

    @PostMapping("/calculate")
    public ShippingFeeResponse calculate(@Valid @RequestBody ShippingFeeRequest request) {
        return shippingFeeService.calculate(
                request.getProvinceId(),
                request.getSubtotal()
        );
    }
}