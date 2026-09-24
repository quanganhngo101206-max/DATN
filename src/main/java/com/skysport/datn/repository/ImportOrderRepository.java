package com.skysport.datn.repository;

import com.skysport.datn.entity.ImportOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ImportOrderRepository extends JpaRepository<ImportOrder, Integer> {
    List<ImportOrder> findByStaffIdOrderByCreateDateDesc(Integer staffId);
    List<ImportOrder> findAllByOrderByCreateDateDesc();
    List<ImportOrder> findByStatus(Integer status);

    /** Tổng chi phí nhập hàng đã duyệt (status=2) trong khoảng ngày */
    @Query("SELECT COALESCE(SUM(io.totalAmount), 0) FROM ImportOrder io " +
            "WHERE io.status = 2 " +
            "AND io.createDate >= :from " +
            "AND io.createDate < :to")
    Long sumApprovedImportCost(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
