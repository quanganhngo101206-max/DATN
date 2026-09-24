package com.skysport.datn.repository;

import com.skysport.datn.entity.BillDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    /**
     * Lấy BillDetail của các đơn HOÀN THÀNH (status=7) trong khoảng ngày.
     * Dùng cho StatisticsService tính COGS per-day, top danh mục.
     * JOIN FETCH để tránh N+1 khi truy cập productDetail.product.category.
     */
    @Query("SELECT bd FROM BillDetail bd " +
            "JOIN FETCH bd.productDetail pd " +
            "JOIN FETCH pd.product p " +
            "LEFT JOIN FETCH p.category " +
            "WHERE bd.bill.status = 7 " +
            "AND bd.bill.createDate >= :from " +
            "AND bd.bill.createDate < :to")
    List<BillDetail> findCompletedInRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ==========================================================
    // SQL AGGREGATION — thay thế findAllCompleted() + Java group
    // ==========================================================

    /**
     * Top sản phẩm bán chạy (theo số lượng thực bán = qty - returnQty)
     * trong khoảng ngày [from, to), chỉ tính đơn HOÀN THÀNH (status=7).
     * Trả về: [productDetailId, productId, productName, totalSold, totalRevenue]
     * Group theo productDetail (biến thể) — không group thẳng theo product — vì
     * costMap (loadAverageImportCosts()) cũng khóa theo productDetailId; service
     * ghép cost ở đây rồi mới cộng dồn lên theo sản phẩm, tránh cost=0 giả cho mọi dòng.
     *
     * Gọi với from=null để lấy all-time (không giới hạn ngày).
     */
    @Query("SELECT pd.id, pd.product.id, pd.product.name, " +
            "  SUM(CASE WHEN bd.returnQuantity IS NOT NULL THEN bd.quantity - bd.returnQuantity ELSE bd.quantity END), " +
            "  SUM(bd.momentPrice * " +
            "      CASE WHEN bd.returnQuantity IS NOT NULL THEN bd.quantity - bd.returnQuantity ELSE bd.quantity END) " +
            "FROM BillDetail bd " +
            "JOIN bd.productDetail pd " +
            "JOIN pd.product p " +
            "WHERE bd.bill.status = 7 " +
            "AND (:from IS NULL OR bd.bill.createDate >= :from) " +
            "AND (:to IS NULL OR bd.bill.createDate < :to) " +
            "GROUP BY pd.id, pd.product.id, pd.product.name")
    List<Object[]> aggregateTopProducts(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Tổng COGS (số lượng thực bán × giá nhập trung bình) trong khoảng ngày.
     * Tính COGS gần đúng: SUM(qty_net * momentPrice) — dùng momentPrice làm proxy
     * vì giá vốn thực (importPrice) cần join thêm ImportOrderDetail.
     *
     * Nếu cần giá vốn chính xác, dùng computeCogsInRange() với costMap từ ImportOrderDetail.
     * Query này chỉ dùng để ước lượng nhanh khi không có range (all-time).
     *
     * Trả về: tổng (qty_net * momentPrice) — dùng làm cogs proxy.
     */
    @Query("SELECT COALESCE(SUM(bd.momentPrice * " +
            "  CASE WHEN bd.returnQuantity IS NOT NULL THEN bd.quantity - bd.returnQuantity ELSE bd.quantity END), 0) " +
            "FROM BillDetail bd " +
            "WHERE bd.bill.status = 7 " +
            "AND (:from IS NULL OR bd.bill.createDate >= :from) " +
            "AND (:to IS NULL OR bd.bill.createDate < :to)")
    Long sumNetRevenue(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Top danh mục theo doanh thu trong khoảng ngày.
     * Trả về: [categoryId, categoryName, totalSold, totalRevenue]
     */
    @Query("SELECT p.category.id, p.category.name, " +
            "  SUM(CASE WHEN bd.returnQuantity IS NOT NULL THEN bd.quantity - bd.returnQuantity ELSE bd.quantity END), " +
            "  SUM(bd.momentPrice * " +
            "      CASE WHEN bd.returnQuantity IS NOT NULL THEN bd.quantity - bd.returnQuantity ELSE bd.quantity END) " +
            "FROM BillDetail bd " +
            "JOIN bd.productDetail pd " +
            "JOIN pd.product p " +
            "WHERE bd.bill.status = 7 " +
            "AND p.category IS NOT NULL " +
            "AND bd.bill.createDate >= :from " +
            "AND bd.bill.createDate < :to " +
            "GROUP BY p.category.id, p.category.name " +
            "ORDER BY SUM(bd.momentPrice * " +
            "  CASE WHEN bd.returnQuantity IS NOT NULL THEN bd.quantity - bd.returnQuantity ELSE bd.quantity END) DESC")
    List<Object[]> aggregateTopCategories(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * COGS per ngày trong khoảng [from, to) — dùng cho buildDailyProfit.
     * Trả về: [date (LocalDate), cogsByProxy (Long)]
     * cogsByProxy = SUM(momentPrice * qty_net) × hệ số costMap/revenue (gần đúng).
     *
     * Lưu ý: để có COGS chính xác theo importPrice, vẫn cần load BillDetail trong range
     * rồi tra costMap. Query này trả dữ liệu đã group sẵn theo ngày để tránh load entity.
     * Trả về: [date, qty_net, revenue_proxy] để StatisticsService tính với costMap bên ngoài.
     */
    @Query("SELECT CAST(bd.bill.createDate AS LocalDate), " +
            "  bd.productDetail.id, " +
            "  SUM(CASE WHEN bd.returnQuantity IS NOT NULL THEN bd.quantity - bd.returnQuantity ELSE bd.quantity END) " +
            "FROM BillDetail bd " +
            "WHERE bd.bill.status = 7 " +
            "AND bd.bill.createDate >= :from " +
            "AND bd.bill.createDate < :to " +
            "GROUP BY CAST(bd.bill.createDate AS LocalDate), bd.productDetail.id")
    List<Object[]> aggregateDailyQtyByProductDetail(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}