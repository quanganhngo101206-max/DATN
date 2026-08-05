package com.skysport.datn.controller.admin;

import com.skysport.datn.entity.Bill;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.repository.BillRepository;
import com.skysport.datn.repository.CustomerRepository;
import com.skysport.datn.repository.ProductDetailRepository;
import com.skysport.datn.repository.ProductRepository;
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

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        List<Bill> allBills = billRepository.findAll();

        // ===== STAT CARDS =====
        double totalRevenue = allBills.stream()
                .filter(b -> b.getStatus() != null && OrderStatus.COMPLETED.matches(b.getStatus()))
                .mapToDouble(b -> b.getAmount() != null ? b.getAmount() : 0)
                .sum();
        model.addAttribute("totalRevenue", (long) totalRevenue);
        model.addAttribute("totalOrders", allBills.size());
        model.addAttribute("totalProducts", productRepository.count());
        model.addAttribute("totalCustomers", customerRepository.count());

        // ===== BIỂU ĐỒ DOANH THU 7 NGÀY QUA =====
        LocalDate today = LocalDate.now();
        DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("dd/MM");

        List<String> revenueLabels = new ArrayList<>();
        List<Long> revenueData = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            revenueLabels.add(date.format(labelFormatter));

            double dayRevenue = allBills.stream()
                    .filter(b -> b.getStatus() != null && OrderStatus.COMPLETED.matches(b.getStatus()))
                    .filter(b -> b.getCreateDate() != null
                            && b.getCreateDate().toLocalDate().equals(date))
                    .mapToDouble(b -> b.getAmount() != null ? b.getAmount() : 0)
                    .sum();
            revenueData.add((long) dayRevenue);
        }

        model.addAttribute("revenueLabels", revenueLabels);
        model.addAttribute("revenueData", revenueData);

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

        // ===== TOP 5 SẢN PHẨM BÁN CHẠY =====
        Map<String, long[]> productStats = new LinkedHashMap<>();

        allBills.stream()
                .filter(b -> b.getStatus() != null && OrderStatus.COMPLETED.matches(b.getStatus()))
                .forEach(bill -> {
                    try {
                        bill.getBillDetails().forEach(detail -> {
                            String name = detail.getProductDetail().getProduct().getName();
                            long qty = detail.getQuantity() != null ? detail.getQuantity() : 0;
                            long rev = detail.getMomentPrice() != null
                                    ? (long) (detail.getMomentPrice() * qty) : 0;
                            productStats.merge(name, new long[]{qty, rev},
                                    (a, b) -> new long[]{a[0] + b[0], a[1] + b[1]});
                        });
                    } catch (Exception ignored) {}
                });

        List<Map<String, Object>> topProducts = productStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productName", e.getKey());
                    m.put("totalSold", e.getValue()[0]);
                    m.put("revenue", e.getValue()[1]);
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