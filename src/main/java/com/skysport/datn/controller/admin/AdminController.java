package com.skysport.datn.controller.admin;

import com.skysport.datn.dto.response.DashboardStatsDto;
import com.skysport.datn.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final StatisticsService statisticsService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        DashboardStatsDto stats = statisticsService.getAdminDashboardStats();

        model.addAttribute("totalRevenue", stats.getTotalRevenue());
        model.addAttribute("todayRevenue", stats.getTodayRevenue());
        model.addAttribute("totalOrders", stats.getTotalOrders());
        model.addAttribute("todayOrders", stats.getTodayOrders());
        model.addAttribute("totalProducts", stats.getTotalProducts());
        model.addAttribute("totalCustomers", stats.getTotalCustomers());
        model.addAttribute("revenueLabels", stats.getRevenueLabels());
        model.addAttribute("revenueData", stats.getRevenueData());
        model.addAttribute("statusLabels", stats.getStatusLabels());
        model.addAttribute("statusData", stats.getStatusData());
        model.addAttribute("topProducts", stats.getTopProducts());
        model.addAttribute("recentOrders", stats.getRecentOrders());
        model.addAttribute("lowStockList", stats.getLowStockList());
        model.addAttribute("lowStockCount", stats.getLowStockCount());
        model.addAttribute("pendingCount", stats.getPendingOrders());
        return "admin/dashboard";
    }
}