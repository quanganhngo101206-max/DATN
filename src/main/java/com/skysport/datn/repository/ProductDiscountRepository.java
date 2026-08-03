package com.skysport.datn.repository;

import com.skysport.datn.entity.ProductDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductDiscountRepository extends JpaRepository<ProductDiscount, Integer> {

    // Lấy khuyến mãi đang còn hiệu lực (chưa đóng) của 1 biến thể sản phẩm
    Optional<ProductDiscount> findByProductDetail_IdAndClosedFalse(Integer productDetailId);
}