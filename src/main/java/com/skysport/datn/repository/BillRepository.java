package com.skysport.datn.repository;

import com.skysport.datn.entity.Bill;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface BillRepository extends JpaRepository<Bill, Integer> {

    // Module Hóa đơn (admin/staff) chỉ quản lý đơn ONLINE (invoiceType=1) — POS có
    // lifecycle riêng (posStatus) và đã có màn /pos riêng, không nên lẫn vào đây
    // (nếu lẫn vào, "Xác nhận"/"Hủy" ở Hóa đơn sẽ đổi Bill.status của đơn POS mà
    // không đổi Bill.posStatus tương ứng, gây lệch trạng thái).
    List<Bill> findAllByInvoiceTypeOrderByCreateDateDesc(Integer invoiceType);

    List<Bill> findByStatusAndInvoiceTypeOrderByCreateDateDesc(Integer status, Integer invoiceType);

    long countByStatus(Integer status);

    Page<Bill> findAllByInvoiceTypeOrderByCreateDateDesc(Integer invoiceType, Pageable pageable);

    Page<Bill> findByStatusAndInvoiceTypeOrderByCreateDateDesc(Integer status, Integer invoiceType, Pageable pageable);

    // invoiceType = 1: chỉ lấy đơn online, loại trừ đơn POS (invoiceType = 2)
    // tránh hiện đơn quầy trong trang "Đơn hàng của tôi" với trạng thái và nút không phù hợp
    @Query("SELECT b FROM Bill b WHERE b.customer.id = :customerId AND b.invoiceType = 1 ORDER BY b.createDate DESC")
    List<Bill> findByCustomerId(@Param("customerId") Integer customerId);

    @Query("SELECT MAX(b.id) FROM Bill b")
    Integer findMaxId();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bill b WHERE b.id = :id")
    Optional<Bill> findByIdForUpdate(@Param("id") Integer id);

    Bill findByCode(String code);

    Optional<Bill> findByVnpTxnRef(String vnpTxnRef);

    List<Bill> findByInvoiceTypeAndPosStatusOrderByCreateDateDesc(Integer invoiceType, Integer posStatus);

    List<Bill> findByInvoiceTypeAndPosStatusOrderByUpdateDateDesc(Integer invoiceType, Integer posStatus);

    @Query("SELECT b FROM Bill b WHERE b.customer.id = :customerId " +
            "AND b.discountCode.id = :discountCodeId " +
            "AND b.status <> 5 " +
            "AND (b.invoiceType <> 2 OR b.invoiceType IS NULL OR b.posStatus IS NULL OR b.posStatus = 2)")
    List<Bill> findByCustomerIdAndDiscountCodeIdExcludingCancelled(@Param("customerId") Integer customerId,
                                                                   @Param("discountCodeId") Integer discountCodeId);

    @Query("SELECT b FROM Bill b WHERE b.invoiceType = 1 AND b.status = 1 " +
            "AND b.createDate < :cutoff")
    List<Bill> findPendingOnlineBillsCreatedBefore(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Đơn POS còn WAITING (posStatus=1) tạo trước mốc cutoff — dùng để tự hết hạn
     * đơn quầy bị bỏ dở qua ngày hôm sau. invoiceType=2 là POS, tách hẳn khỏi
     * findPendingOnlineBillsCreatedBefore() vốn chỉ xử lý invoiceType=1 (online).
     */
    @Query("SELECT b FROM Bill b WHERE b.invoiceType = 2 AND b.posStatus = 1 " +
            "AND b.createDate < :cutoff")
    List<Bill> findWaitingPosBillsCreatedBefore(@Param("cutoff") LocalDateTime cutoff);

    // ===== Queries cho StatisticsService =====

    /**
     * Tổng doanh thu theo trạng thái + khoảng ngày.
     * Khi from/to không null (trường hợp thường dùng): SQL Server có thể dùng index createDate.
     * Khi from IS NULL / to IS NULL (aggregate toàn thời gian): SQL scan toàn bảng — chấp nhận được
     * vì chỉ xảy ra cho stat "all-time" trên dashboard admin (tần suất thấp).
     */
    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Bill b " +
            "WHERE b.status = :status " +
            "AND (:from IS NULL OR b.createDate >= :from) " +
            "AND (:to IS NULL OR b.createDate < :to)")
    Long sumAmountByStatusAndDateRange(
            @Param("status") Integer status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Tổng doanh thu theo trạng thái + invoiceType + khoảng ngày.
     * Dùng để tách Online vs POS revenue một cách chính xác.
     */
    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Bill b " +
            "WHERE b.status = :status " +
            "AND b.invoiceType = :invoiceType " +
            "AND b.createDate >= :from " +
            "AND b.createDate < :to")
    Long sumAmountByStatusAndInvoiceTypeAndDateRange(
            @Param("status") Integer status,
            @Param("invoiceType") Integer invoiceType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Đếm đơn theo trạng thái trong khoảng ngày */
    @Query("SELECT COUNT(b) FROM Bill b " +
            "WHERE b.status = :status " +
            "AND (:from IS NULL OR b.createDate >= :from) " +
            "AND (:to IS NULL OR b.createDate < :to)")
    Long countByStatusAndDateRange(
            @Param("status") Integer status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Đếm đơn theo trạng thái + invoiceType + khoảng ngày.
     * Dùng để tách số đơn Online vs POS một cách chính xác (thay vì lấy chung
     * countByStatusAndDateRange rồi coi POS = 0).
     */
    @Query("SELECT COUNT(b) FROM Bill b " +
            "WHERE b.status = :status " +
            "AND b.invoiceType = :invoiceType " +
            "AND b.createDate >= :from " +
            "AND b.createDate < :to")
    Long countByStatusAndInvoiceTypeAndDateRange(
            @Param("status") Integer status,
            @Param("invoiceType") Integer invoiceType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Doanh thu theo ngày (GROUP BY) — trả [date, sum] */
    @Query("SELECT CAST(b.createDate AS LocalDate), COALESCE(SUM(b.amount), 0) " +
            "FROM Bill b " +
            "WHERE b.status = :status " +
            "AND b.createDate >= :from AND b.createDate < :to " +
            "GROUP BY CAST(b.createDate AS LocalDate)")
    List<Object[]> sumAmountGroupByDate(
            @Param("status") Integer status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Top khách hàng: [customerId, customerName, orderCount, totalRevenue] */
    @Query("SELECT b.customer.id, b.customer.name, COUNT(b), COALESCE(SUM(b.amount), 0) " +
            "FROM Bill b " +
            "WHERE b.status = :status " +
            "AND b.customer IS NOT NULL " +
            "AND (:from IS NULL OR b.createDate >= :from) " +
            "AND (:to IS NULL OR b.createDate < :to) " +
            "GROUP BY b.customer.id, b.customer.name " +
            "ORDER BY SUM(b.amount) DESC")
    List<Object[]> findTopCustomers(
            @Param("status") Integer status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /**
     * Phân trang đơn hàng với filter.
     *
     * Lưu ý về LOWER(...) LIKE '%keyword%': với dữ liệu lớn sẽ không dùng được index thường.
     * Giải pháp production: thêm Full-Text Index trên cột code + customer.name, dùng CONTAINS().
     * Hiện tại giữ LIKE để đơn giản cho DATN; cần thêm index FTS nếu data > 10.000 đơn.
     */
    @Query("SELECT b FROM Bill b " +
            "WHERE b.createDate >= :from " +
            "AND b.createDate < :to " +
            "AND (:status IS NULL OR :status = 0 OR b.status = :status) " +
            "AND (:invoiceType IS NULL OR b.invoiceType = :invoiceType) " +
            "AND (:keyword IS NULL OR :keyword = '' " +
            "  OR LOWER(b.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "  OR LOWER(b.customer.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY b.createDate DESC")
    Page<Bill> searchOrders(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") Integer status,
            @Param("invoiceType") Integer invoiceType,
            @Param("keyword") String keyword,
            Pageable pageable);

    /** Đếm đơn trong ngày — from/to luôn không null khi dùng */
    @Query("SELECT COUNT(b) FROM Bill b " +
            "WHERE b.createDate >= :from AND b.createDate < :to")
    Long countByDateRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** 5 đơn gần nhất */
    List<Bill> findTop5ByOrderByCreateDateDesc();
}