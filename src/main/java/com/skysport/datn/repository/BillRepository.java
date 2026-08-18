package com.skysport.datn.repository;

import com.skysport.datn.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface BillRepository extends JpaRepository<Bill, Integer> {

    List<Bill> findAllByOrderByCreateDateDesc();

    List<Bill> findByStatusOrderByCreateDateDesc(Integer status);

    // Đếm số đơn hàng theo trạng thái (dùng cho badge "đơn hàng mới" - status=1: Chờ xác nhận)
    long countByStatus(Integer status);

    // Phân trang
    Page<Bill> findAllByOrderByCreateDateDesc(Pageable pageable);

    Page<Bill> findByStatusOrderByCreateDateDesc(Integer status, Pageable pageable);

    @Query("SELECT b FROM Bill b WHERE b.customer.id = :customerId ORDER BY b.createDate DESC")
    List<Bill> findByCustomerId(@Param("customerId") Integer customerId);

    @Query("SELECT MAX(b.id) FROM Bill b")
    Integer findMaxId();

    Bill findByCode(String code);

    // Kiểm tra khách hàng đã từng dùng mã giảm giá này (đơn không bị hủy) chưa
    @Query("SELECT COUNT(b) FROM Bill b WHERE b.customer.id = :customerId " +
            "AND b.discountCode.id = :discountCodeId AND b.status <> 5")
    long countByCustomerIdAndDiscountCodeIdExcludingCancelled(@Param("customerId") Integer customerId,
                                                              @Param("discountCodeId") Integer discountCodeId);
}