package com.skysport.datn.repository;

import com.skysport.datn.entity.ImportOrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportOrderDetailRepository extends JpaRepository<ImportOrderDetail, Integer> {
    List<ImportOrderDetail> findByImportOrderId(Integer importOrderId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(d.importPrice * d.quantity), 0) FROM ImportOrderDetail d WHERE d.importOrder.id = :orderId")
    Double sumTotalByOrderId(@org.springframework.data.repository.query.Param("orderId") Integer orderId);
}