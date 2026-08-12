package com.skysport.datn.repository;

import com.skysport.datn.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {
    List<Product> findByDeleteFlag(Boolean deleteFlag);
    List<Product> findByDeleteFlagFalse();

    // Tìm kiếm + lọc + phân trang cho trang quản trị.
    // Mỗi điều kiện chỉ áp dụng khi tham số tương ứng khác null (bỏ qua nếu không lọc theo tiêu chí đó).
    // size/color nằm ở Product_detail nên lọc bằng EXISTS subquery; dùng DISTINCT để tránh nhân bản dòng.
    @Query("SELECT DISTINCT p FROM Product p WHERE p.deleteFlag = false " +
            "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "     OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:brandId IS NULL OR p.brand.id = :brandId) " +
            "AND (:materialId IS NULL OR p.material.id = :materialId) " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:sizeId IS NULL OR EXISTS (SELECT 1 FROM ProductDetail pd WHERE pd.product = p " +
            "     AND pd.deleteFlag = false AND pd.size.id = :sizeId)) " +
            "AND (:colorId IS NULL OR EXISTS (SELECT 1 FROM ProductDetail pd WHERE pd.product = p " +
            "     AND pd.deleteFlag = false AND pd.color.id = :colorId))")
    Page<Product> search(@Param("keyword") String keyword,
                         @Param("categoryId") Integer categoryId,
                         @Param("brandId") Integer brandId,
                         @Param("materialId") Integer materialId,
                         @Param("sizeId") Integer sizeId,
                         @Param("colorId") Integer colorId,
                         @Param("status") Integer status,
                         Pageable pageable);
}