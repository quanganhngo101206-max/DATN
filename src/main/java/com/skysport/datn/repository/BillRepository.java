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

    // Danh sách đơn POS đang chờ
    List<Bill> findByInvoiceTypeAndPosStatusOrderByCreateDateDesc(Integer invoiceType, Integer posStatus);

    // Danh sách đơn POS đã hoàn thành
    List<Bill> findByInvoiceTypeAndPosStatusOrderByUpdateDateDesc(Integer invoiceType, Integer posStatus);

    // Kiểm tra khách hàng đã từng dùng mã giảm giá này chưa
    // POS chỉ được tính khi đã hoàn thành
    @Query("SELECT COUNT(b) FROM Bill b WHERE b.customer.id = :customerId " +
            "AND b.discountCode.id = :discountCodeId " +
            "AND b.status <> 5 " +
            "AND (b.invoiceType <> 2 OR b.invoiceType IS NULL OR b.posStatus IS NULL OR b.posStatus = 2)")
    long countByCustomerIdAndDiscountCodeIdExcludingCancelled(@Param("customerId") Integer customerId,
                                                              @Param("discountCodeId") Integer discountCodeId);

    /**
     * Đơn online (invoiceType=1) đang ở trạng thái PENDING (status=1),
     * tạo trước thời điểm cutoff. Dùng cho job tự hủy đơn "Chuyển khoản"
     * bị khách bỏ dở giữa chừng (không hoàn tất bước mock-vnpay-pay),
     * tránh giữ tồn kho vĩnh viễn.
     * Lọc phương thức thanh toán (banking hay không) được thực hiện ở
     * tầng service/job vì tên Payment có thể thay đổi theo dữ liệu.
     */
    @Query("SELECT b FROM Bill b WHERE b.invoiceType = 1 AND b.status = 1 " +
            "AND b.createDate < :cutoff")
    List<Bill> findPendingOnlineBillsCreatedBefore(@Param("cutoff") java.time.LocalDateTime cutoff);
}