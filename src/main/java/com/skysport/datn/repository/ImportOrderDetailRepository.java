package com.skysport.datn.repository;

import com.skysport.datn.entity.ImportOrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportOrderDetailRepository extends JpaRepository<ImportOrderDetail, Integer> {
    List<ImportOrderDetail> findByImportOrderId(Integer importOrderId);

    @Query("SELECT COALESCE(SUM(d.importPrice * d.quantity), 0) FROM ImportOrderDetail d WHERE d.importOrder.id = :orderId")
    Double sumTotalByOrderId(@Param("orderId") Integer orderId);

    @Query("SELECT d.productDetail.id, SUM(d.importPrice * d.quantity) / SUM(d.quantity) " +
            "FROM ImportOrderDetail d " +
            "WHERE d.importOrder.status = 2 " +
            "AND d.importPrice IS NOT NULL AND d.quantity IS NOT NULL AND d.quantity > 0 " +
            "AND d.productDetail IS NOT NULL " +
            "GROUP BY d.productDetail.id")
    List<Object[]> findAverageImportPriceByProductDetail();
}