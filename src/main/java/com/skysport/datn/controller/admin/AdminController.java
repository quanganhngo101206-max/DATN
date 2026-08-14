package com.skysport.datn.controller.admin;

import com.skysport.datn.entity.Bill;
import com.skysport.datn.repository.BillRepository;
import com.skysport.datn.repository.CustomerRepository;
import com.skysport.datn.repository.ProductDetailRepository;
import com.skysport.datn.repository.ProductRepository;
import com.skysport.datn.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductDetailRepository productDetailRepository;

    @Autowired
    private StatisticsService statisticsService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        List<Bill> allBills = billRepository.findAll();

        // ===== STAT CARDS =====
        StatisticsService.RevenueSummary summary = statisticsService.summarize(allBills);
        model.addAttribute("totalRevenue", (long) summary.revenue());
        model.addAttribute("totalCost", (long) summary.cost());
        model.addAttribute("totalProfit", (long) summary.profit());
        model.addAttribute("totalOrders", allBills.size());
        model.addAttribute("totalProducts", productRepository.count());
        model.addAttribute("totalCustomers", customerRepository.count());

        // ===== BIỂU ĐỒ DOANH THU 7 NGÀY QUA =====
        List<StatisticsService.RevenueChartPoint> chart = statisticsService.getRevenueChart(allBills, 7);
        model.addAttribute("revenueLabels", chart.stream().map(StatisticsService.RevenueChartPoint::label).toList());
        model.addAttribute("revenueData", chart.stream().map(StatisticsService.RevenueChartPoint::revenue).toList());

        // ===== BIỂU ĐỒ TRẠNG THÁI ĐƠN HÀNG =====
        Map<Integer, Long> statusCount = allBills.stream()
                .filter(b -> b.getStatus() != null)
                .collect(Collectors.groupingBy(Bill::getStatus, Collectors.counting()));

        model.addAttribute("statusLabels",
                List.of("Chờ xác nhận", "Đã xác nhận", "Đang giao", "Hoàn thành", "Đã hủy"));
        model.addAttribute("statusData", List.of(
                statusCount.getOrDefault(1, 0L),
                statusCount.getOrDefault(2, 0L),
                statusCount.getOrDefault(3, 0L),
                statusCount.getOrDefault(7, 0L),
                statusCount.getOrDefault(5, 0L)
        ));

        // ===== TOP 5 SẢN PHẨM BÁN CHẠY (kèm giá bán bình quân + giá vốn + lợi nhuận) =====
        List<Map<String, Object>> topProducts = statisticsService.getTopProducts(allBills, 5).stream()
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productName", p.productName());
                    m.put("totalSold", p.totalSold());
                    m.put("revenue", (long) p.revenue());
                    m.put("avgSellingPrice", (long) p.avgSellingPrice());
                    m.put("cost", (long) p.cost());
                    m.put("profit", (long) p.profit());
                    return m;
                })
                .collect(Collectors.toList());

        model.addAttribute("topProducts", topProducts);

        // ===== 5 ĐƠN HÀNG MỚI NHẤT =====
        List<Map<String, Object>> recentOrders = allBills.stream()
                .sorted((a, b) -> {
                    if (a.getCreateDate() == null) return 1;
                    if (b.getCreateDate() == null) return -1;
                    return b.getCreateDate().compareTo(a.getCreateDate());
                })
                .limit(5)
                .map(b -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("code", b.getCode());
                    m.put("customerName", b.getCustomer() != null ? b.getCustomer().getName() : "Khách lẻ");
                    m.put("amount", b.getAmount() != null ? b.getAmount().longValue() : 0L);
                    m.put("status", b.getStatus());
                    m.put("statusText", getStatusText(b.getStatus()));
                    return m;
                })
                .collect(Collectors.toList());

        model.addAttribute("recentOrders", recentOrders);

        // ✅ ===== THÊM: TỒN KHO THẤP (≤ 10) =====
        List<Map<String, Object>> lowStockList = productDetailRepository.findAll().stream()
                .filter(pd -> pd.getDeleteFlag() == null || !pd.getDeleteFlag())
                .filter(pd -> pd.getQuantity() != null && pd.getQuantity() <= 10 && pd.getQuantity() > 0)
                .sorted((a, b) -> Integer.compare(
                        a.getQuantity() != null ? a.getQuantity() : 0,
                        b.getQuantity() != null ? b.getQuantity() : 0))
                .limit(10)
                .map(pd -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productName", pd.getProduct() != null ? pd.getProduct().getName() : "-");
                    m.put("size", pd.getSize() != null ? pd.getSize().getName() : "-");
                    m.put("color", pd.getColor() != null ? pd.getColor().getName() : "-");
                    m.put("quantity", pd.getQuantity());
                    return m;
                })
                .collect(Collectors.toList());

        model.addAttribute("lowStockList", lowStockList);
        model.addAttribute("lowStockCount", lowStockList.size());  // Số lượng sản phẩm sắp hết

        return "admin/dashboard";
    }

    private String getStatusText(Integer status) {
        if (status == null) return "Không xác định";
        return switch (status) {
            case 1 -> "Chờ xác nhận";
            case 2 -> "Đã xác nhận";
            case 3 -> "Đang giao";
            case 4 -> "Đã giao";
            case 5 -> "Đã hủy";
            case 6 -> "Trả hàng";
            case 7 -> "Hoàn thành";
            default -> "Không xác định";
        };
    }
}