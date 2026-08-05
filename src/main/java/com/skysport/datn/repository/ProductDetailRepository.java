package com.skysport.datn.repository;

import com.skysport.datn.entity.ProductDetail;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductDetailRepository extends JpaRepository<ProductDetail, Integer> {
    List<ProductDetail> findByProductId(Integer productId);
    List<ProductDetail> findByProductIdAndDeleteFlagFalse(Integer productId);

    // Tìm biến thể theo product + size + color để chặn trùng (bao gồm cả bản ghi đã xóa mềm)
    Optional<ProductDetail> findByProduct_IdAndSize_IdAndColor_Id(Integer productId, Integer sizeId, Integer colorId);

    // Khóa pessimistic write — dùng khi checkout để tránh race condition trừ tồn kho
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pd FROM ProductDetail pd WHERE pd.id = :id")
    Optional<ProductDetail> findByIdForUpdate(@Param("id") Integer id);

    // Lấy id sản phẩm đang có khuyến mãi (sale) còn hiệu lực, dùng cho gợi ý bán tại quầy
    @Query("SELECT DISTINCT pd.product.id FROM ProductDetail pd " +
            "WHERE pd.deleteFlag = false " +
            "AND pd.productDiscount IS NOT NULL " +
            "AND pd.productDiscount.closed = false " +
            "AND pd.productDiscount.startDate <= :now " +
            "AND pd.productDiscount.endDate >= :now")
    List<Integer> findProductIdsOnSale(@Param("now") LocalDateTime now);
}