package com.skysport.datn.service;

import com.skysport.datn.entity.Bill;
import com.skysport.datn.entity.BillDetail;
import com.skysport.datn.entity.ImportOrderDetail;
import com.skysport.datn.enums.ImportOrderStatus;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.repository.ImportOrderDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tính doanh thu, giá vốn và thu nhập (lợi nhuận) cho dashboard admin/staff.
 * Gom về 1 chỗ để 2 dashboard luôn ra cùng một con số, tránh lệch nhau như phần
 * hiển thị trạng thái đơn hàng trước đây.
 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final ImportOrderDetailRepository importOrderDetailRepository;

    public record RevenueSummary(double revenue, double cost, double profit) {}

    public record ProductStat(String productName, long totalSold, double revenue,
                              double avgSellingPrice, double cost, double profit) {}

    public record RevenueChartPoint(String label, long revenue) {}

    /**
     * Giá vốn bình quân gia quyền theo từng biến thể sản phẩm (Product_detail),
     * tính từ các phiếu nhập đã được duyệt (ImportOrderStatus.APPROVED).
     * Biến thể chưa từng được nhập (hoặc phiếu nhập chưa duyệt) sẽ không có trong map -> coi giá vốn = 0.
     */
    public Map<Integer, Double> getAverageCostByProductDetail() {
        Map<Integer, double[]> agg = new HashMap<>(); // [tổng tiền nhập, tổng số lượng nhập]
        for (ImportOrderDetail d : importOrderDetailRepository.findAll()) {
            if (d.getImportOrder() == null || !ImportOrderStatus.APPROVED.matches(d.getImportOrder().getStatus())) {
                continue;
            }
            if (d.getProductDetail() == null || d.getQuantity() == null || d.getImportPrice() == null) {
                continue;
            }
            double[] cur = agg.computeIfAbsent(d.getProductDetail().getId(), k -> new double[2]);
            cur[0] += d.getImportPrice() * d.getQuantity();
            cur[1] += d.getQuantity();
        }
        Map<Integer, Double> result = new HashMap<>();
        agg.forEach((k, v) -> result.put(k, v[1] > 0 ? v[0] / v[1] : 0));
        return result;
    }

    /** Doanh thu / giá vốn / thu nhập, chỉ tính trên các đơn đã hoàn thành (COMPLETED) trong danh sách truyền vào */
    public RevenueSummary summarize(List<Bill> bills) {
        Map<Integer, Double> avgCost = getAverageCostByProductDetail();
        double revenue = 0, cost = 0;
        for (Bill b : bills) {
            if (b.getStatus() == null || !OrderStatus.COMPLETED.matches(b.getStatus())) continue;
            revenue += b.getAmount() != null ? b.getAmount() : 0;
            if (b.getBillDetails() == null) continue;
            for (BillDetail d : b.getBillDetails()) {
                if (d.getProductDetail() == null || d.getQuantity() == null) continue;
                double unitCost = avgCost.getOrDefault(d.getProductDetail().getId(), 0.0);
                cost += unitCost * d.getQuantity();
            }
        }
        return new RevenueSummary(revenue, cost, revenue - cost);
    }

    /** Doanh thu N ngày gần nhất (chỉ tính đơn đã hoàn thành), dùng cho biểu đồ */
    public List<RevenueChartPoint> getRevenueChart(List<Bill> bills, int days) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        List<RevenueChartPoint> points = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            long dayRevenue = (long) bills.stream()
                    .filter(b -> b.getStatus() != null && OrderStatus.COMPLETED.matches(b.getStatus()))
                    .filter(b -> b.getCreateDate() != null && b.getCreateDate().toLocalDate().equals(date))
                    .mapToDouble(b -> b.getAmount() != null ? b.getAmount() : 0)
                    .sum();
            points.add(new RevenueChartPoint(date.format(fmt), dayRevenue));
        }
        return points;
    }

    /** Top N sản phẩm bán chạy (theo đơn đã hoàn thành): số lượng, doanh thu, giá bán bình quân, giá vốn, lợi nhuận */
    public List<ProductStat> getTopProducts(List<Bill> bills, int limit) {
        Map<Integer, Double> avgCost = getAverageCostByProductDetail();
        Map<String, double[]> agg = new LinkedHashMap<>(); // [qty, revenue, cost]
        for (Bill b : bills) {
            if (b.getStatus() == null || !OrderStatus.COMPLETED.matches(b.getStatus())) continue;
            if (b.getBillDetails() == null) continue;
            for (BillDetail d : b.getBillDetails()) {
                try {
                    String name = d.getProductDetail().getProduct().getName();
                    double qty = d.getQuantity() != null ? d.getQuantity() : 0;
                    double revenue = d.getMomentPrice() != null ? d.getMomentPrice() * qty : 0;
                    double unitCost = avgCost.getOrDefault(d.getProductDetail().getId(), 0.0);
                    double cost = unitCost * qty;
                    double[] cur = agg.computeIfAbsent(name, k -> new double[3]);
                    cur[0] += qty;
                    cur[1] += revenue;
                    cur[2] += cost;
                } catch (Exception ignored) {
                    // Thiếu liên kết product/productDetail (dữ liệu lỗi) -> bỏ qua dòng này
                }
            }
        }
        return agg.entrySet().stream()
                .sorted((a, b2) -> Double.compare(b2.getValue()[0], a.getValue()[0]))
                .limit(limit)
                .map(e -> {
                    double qty = e.getValue()[0], revenue = e.getValue()[1], cost = e.getValue()[2];
                    double avgPrice = qty > 0 ? revenue / qty : 0;
                    return new ProductStat(e.getKey(), (long) qty, revenue, avgPrice, cost, revenue - cost);
                })
                .toList();
    }
}