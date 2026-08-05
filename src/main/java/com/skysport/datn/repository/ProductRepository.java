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
    @Query("SELECT p FROM Product p WHERE p.deleteFlag = false " +
            "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "     OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:brandId IS NULL OR p.brand.id = :brandId) " +
            "AND (:status IS NULL OR p.status = :status)")
    Page<Product> search(@Param("keyword") String keyword,
                         @Param("categoryId") Integer categoryId,
                         @Param("brandId") Integer brandId,
                         @Param("status") Integer status,
                         Pageable pageable);
}