package com.skysport.datn.controller.staff;

import com.skysport.datn.dto.response.DashboardStatsDto;
import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.Staff;
import com.skysport.datn.repository.StaffRepository;
import com.skysport.datn.service.StatisticsService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffDashboardController {

    private final StatisticsService statisticsService;
    private final StaffRepository staffRepository;

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Account account = (Account) session.getAttribute("account");
        if (account != null) {
            Staff staff = staffRepository.findByAccountId(account.getId());
            model.addAttribute("staff", staff);
        }

        DashboardStatsDto stats = statisticsService.getStaffDashboardStats();
        model.addAttribute("pendingOrders", stats.getPendingOrders());
        model.addAttribute("shippingOrders", stats.getShippingOrders());
        model.addAttribute("todayOrders", stats.getTodayOrders());
        model.addAttribute("todayRevenue", stats.getTodayRevenue());
        model.addAttribute("recentOrders", stats.getRecentOrders());
        model.addAttribute("lowStockList", stats.getLowStockList());

        return "staff/dashboard";
    }
}
