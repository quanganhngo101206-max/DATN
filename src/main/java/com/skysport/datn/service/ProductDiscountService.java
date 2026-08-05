package com.skysport.datn.service;

import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.entity.ProductDiscount;
import com.skysport.datn.repository.ProductDetailRepository;
import com.skysport.datn.repository.ProductDiscountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ProductDiscountService {

    @Autowired
    private ProductDiscountRepository productDiscountRepository;

    @Autowired
    private ProductDetailRepository productDetailRepository;

    /**
     * Áp khuyến mãi mới cho 1 biến thể sản phẩm (ProductDetail).
     * Nếu biến thể đang có khuyến mãi còn hiệu lực thì tự động đóng khuyến mãi cũ,
     * tránh trường hợp 1 biến thể có 2 khuyến mãi cùng active.
     */
    @Transactional
    public void createDiscount(Integer productDetailId, Float discountedAmount,
                               LocalDateTime startDate, LocalDateTime endDate) {

        ProductDetail detail = productDetailRepository.findById(productDetailId)
                .orElseThrow(() -> new RuntimeException("Biến thể sản phẩm không tồn tại"));

        if (discountedAmount == null || discountedAmount <= 0) {
            throw new RuntimeException("Số tiền giảm phải lớn hơn 0");
        }
        if (detail.getPrice() != null && discountedAmount >= detail.getPrice()) {
            throw new RuntimeException("Số tiền giảm phải nhỏ hơn giá bán");
        }
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            throw new RuntimeException("Ngày kết thúc phải sau ngày bắt đầu");
        }

        // Đóng khuyến mãi cũ (nếu còn hiệu lực) trước khi tạo mới
        productDiscountRepository.findByProductDetail_IdAndClosedFalse(productDetailId)
                .ifPresent(old -> {
                    old.setClosed(true);
                    productDiscountRepository.save(old);
                });

        ProductDiscount discount = ProductDiscount.builder()
                .discountedAmount(discountedAmount)
                .startDate(startDate)
                .endDate(endDate)
                .closed(false)
                .productDetail(detail)
                .build();
        productDiscountRepository.save(discount);

        // Đồng bộ chiều FK ngược lại trên ProductDetail (schema đang giữ FK 2 chiều)
        detail.setProductDiscount(discount);
        productDetailRepository.save(detail);
    }

    // Tắt khuyến mãi trước hạn
    @Transactional
    public void closeDiscount(Integer discountId) {
        ProductDiscount discount = productDiscountRepository.findById(discountId)
                .orElseThrow(() -> new RuntimeException("Khuyến mãi không tồn tại"));
        discount.setClosed(true);
        productDiscountRepository.save(discount);
    }
}