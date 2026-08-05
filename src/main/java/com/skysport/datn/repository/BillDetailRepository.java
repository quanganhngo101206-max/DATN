package com.skysport.datn.repository;

import com.skysport.datn.entity.BillDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillDetailRepository extends JpaRepository<BillDetail, Integer> {

    List<BillDetail> findByBillId(Integer billId);

    // Top sản phẩm bán chạy (theo tổng số lượng đã bán), dùng cho gợi ý bán tại quầy
    @Query("SELECT bd.productDetail.product.id, SUM(bd.quantity) as totalSold " +
            "FROM BillDetail bd " +
            "WHERE bd.productDetail.product.deleteFlag = false " +
            "GROUP BY bd.productDetail.product.id " +
            "ORDER BY totalSold DESC")
    List<Object[]> findBestSellingProductIds();
}