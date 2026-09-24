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

    /**
     * Query cho chatbot — trả [productId, productName, firstImage, minPrice] trong 1 query.
     *
     * Dùng subquery để lấy ảnh đầu tiên và giá thấp nhất:
     * - Ảnh: (SELECT MIN(img.link) FROM Image img WHERE img.product.id = p.id) — lấy 1 ảnh
     * - Giá: giá thấp nhất của biến thể còn hoạt động
     *
     * Thay thế pattern:
     *   imageRepository.findByProductId(id)         → +N query
     *   productDetailRepository.findByProductId(id) → +N query
     *
     * Lưu ý: MIN(img.link) để lấy 1 ảnh không đảm bảo thứ tự ảnh nhất định.
     * Nếu cần ảnh đầu tiên theo thứ tự insert, cần thêm cột `sort_order` trong bảng Image.
     * Với DATN đây là chấp nhận được.
     */
    @Query("SELECT p.id, p.name, " +
            "  (SELECT MIN(img.link) FROM Image img WHERE img.product.id = p.id), " +
            "  (SELECT MIN(pd.price) FROM ProductDetail pd " +
            "   WHERE pd.product.id = p.id AND pd.deleteFlag = false AND pd.status = 1) " +
            "FROM Product p " +
            "WHERE p.deleteFlag = false AND p.status = 1 " +
            "AND (:keyword IS NULL " +
            "     OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "     OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Object[]> searchForChatbot(
            @Param("keyword") String keyword,
            Pageable pageable);
}