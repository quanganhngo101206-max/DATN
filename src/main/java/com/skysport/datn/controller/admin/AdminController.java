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

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        return "redirect:/admin/report/sales";
    }
}