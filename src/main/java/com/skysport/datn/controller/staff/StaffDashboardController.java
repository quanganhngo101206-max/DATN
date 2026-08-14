package com.skysport.datn.controller.staff;

import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.Bill;
import com.skysport.datn.enums.OrderStatus;
import jakarta.servlet.http.HttpSession;
import com.skysport.datn.entity.Staff;
import com.skysport.datn.repository.BillRepository;
import com.skysport.datn.repository.ProductDetailRepository;
import com.skysport.datn.repository.StaffRepository;
import com.skysport.datn.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/staff")
public class StaffDashboardController {

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private ProductDetailRepository productDetailRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private StatisticsService statisticsService;

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session,Model model) {
        List<Bill> allBills = billRepository.findAll();
        LocalDate today = LocalDate.now();

        Account account = (Account) session.getAttribute("account");
        if (account != null) {
            Staff staff = staffRepository.findByAccountId(account.getId());
            model.addAttribute("staff", staff);  // ✅ THÊM DÒNG NÀY
        }

        // ===== STAT CARDS =====
        long pendingOrders = allBills.stream()
                .filter(b -> b.getStatus() != null && OrderStatus.PENDING.matches(b.getStatus()))
                .count();
        long shippingOrders = allBills.stream()
                .filter(b -> b.getStatus() != null && OrderStatus.SHIPPING.matches(b.getStatus()))
                .count();
        long todayOrders = allBills.stream()
                .filter(b -> b.getCreateDate() != null
                        && b.getCreateDate().toLocalDate().equals(today))
                .count();
        double todayRevenue = allBills.stream()
                .filter(b -> b.getStatus() != null && OrderStatus.COMPLETED.matches(b.getStatus()))
                .filter(b -> b.getCreateDate() != null
                        && b.getCreateDate().toLocalDate().equals(today))
                .mapToDouble(b -> b.getAmount() != null ? b.getAmount() : 0)
                .sum();

        model.addAttribute("pendingOrders", pendingOrders);
        model.addAttribute("shippingOrders", shippingOrders);
        model.addAttribute("todayOrders", todayOrders);
        model.addAttribute("todayRevenue", (long) todayRevenue);

        // ===== DOANH THU / LỢI NHUẬN (toàn thời gian, đơn đã hoàn thành) =====
        StatisticsService.RevenueSummary summary = statisticsService.summarize(allBills);
        model.addAttribute("totalRevenue", (long) summary.revenue());
        model.addAttribute("totalProfit", (long) summary.profit());

        // ===== BIỂU ĐỒ DOANH THU 7 NGÀY QUA =====
        List<StatisticsService.RevenueChartPoint> chart = statisticsService.getRevenueChart(allBills, 7);
        model.addAttribute("revenueLabels", chart.stream().map(StatisticsService.RevenueChartPoint::label).toList());
        model.addAttribute("revenueData", chart.stream().map(StatisticsService.RevenueChartPoint::revenue).toList());

        // ===== TOP 5 SẢN PHẨM BÁN CHẠY =====
        List<Map<String, Object>> topProducts = statisticsService.getTopProducts(allBills, 5).stream()
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productName", p.productName());
                    m.put("totalSold", p.totalSold());
                    m.put("revenue", (long) p.revenue());
                    m.put("avgSellingPrice", (long) p.avgSellingPrice());
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
                    m.put("id", b.getId());
                    m.put("code", b.getCode());
                    m.put("customerName", b.getCustomer() != null ? b.getCustomer().getName() : "Khách lẻ");
                    m.put("amount", b.getAmount() != null ? b.getAmount().longValue() : 0L);
                    m.put("status", b.getStatus());
                    m.put("statusText", getStatusText(b.getStatus()));
                    return m;
                })
                .collect(Collectors.toList());
        model.addAttribute("recentOrders", recentOrders);

        // ===== TỒN KHO THẤP (≤ 10) =====
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

        return "staff/dashboard";
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