package com.skysport.datn.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class DashboardStatsDto {

    private long totalRevenue;
    private long todayRevenue;
    private long grossProfit;
    private long todayProfit;
    private double profitMargin;
    private List<Long> profitData;
    private long totalOrders;
    private long todayOrders;
    private long totalProducts;
    private long totalCustomers;
    private long pendingOrders;
    private long shippingOrders;
    private long lowStockCount;

    private List<String> revenueLabels;
    private List<Long> revenueData;

    private List<String> statusLabels;
    private List<Long> statusData;

    private List<Map<String, Object>> topProducts;
    private List<Map<String, Object>> recentOrders;
    private List<Map<String, Object>> lowStockList;
}
