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
    // Điều kiện "maximumUsage IS NULL OR usedCount < maximumUsage" nằm ngay
    // trong WHERE để DB tự quyết định atomic: nếu 2 request cùng chạy khi chỉ
    // còn đúng 1 lượt, chỉ 1 request UPDATE được 1 dòng (updated=1), request
    // còn lại UPDATE 0 dòng (updated=0) vì điều kiện không còn thỏa sau khi
    // request đầu commit — không thể vượt maximumUsage dù có race.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DiscountCode d SET d.usedCount = COALESCE(d.usedCount, 0) + 1 " +
            "WHERE d.id = :id " +
            "AND (d.maximumUsage IS NULL OR COALESCE(d.usedCount, 0) < d.maximumUsage)")
    int incrementUsedCount(@Param("id") Integer id);

    // Hoàn lượt sử dụng khi đơn hàng đã dùng mã bị hủy. Chặn không cho về âm
    // bằng GREATEST(..., 0) — nếu DB không hỗ trợ GREATEST thì đổi sang
    // CASE WHEN d.usedCount > 0 THEN d.usedCount - 1 ELSE 0 END.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DiscountCode d SET d.usedCount = " +
            "CASE WHEN COALESCE(d.usedCount, 0) > 0 THEN d.usedCount - 1 ELSE 0 END " +
            "WHERE d.id = :id")
    int decrementUsedCount(@Param("id") Integer id);
}