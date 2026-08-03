package com.skysport.datn.repository;

import com.skysport.datn.entity.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Integer> {
    List<DiscountCode> findByDeleteFlagFalse();
    Optional<DiscountCode> findByCode(String code);
    List<DiscountCode> findByDeleteFlag(Boolean deleteFlag);
    // Thêm mới: lọc theo status (chưa xóa mềm)
    List<DiscountCode> findByStatusAndDeleteFlagFalse(Integer status);

    // UPDATE atomic ở tầng DB (không qua read-modify-write của entity) để tránh
    // lost update khi 2 request cùng áp 1 mã giảm giá tại cùng 1 thời điểm.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE DiscountCode d SET d.usedCount = COALESCE(d.usedCount, 0) + 1 WHERE d.id = :id")
    int incrementUsedCount(@Param("id") Integer id);
}