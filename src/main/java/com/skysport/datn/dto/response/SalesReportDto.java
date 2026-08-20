package com.skysport.datn.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SalesReportDto {

    private LocalDate fromDate;
    private LocalDate toDate;

    private long revenue;
    private long orderCount;
    private long completedCount;
    private long cancelledCount;
    private long returningCount;
    private long avgOrderValue;
    private double completionRate;
    private double cancellationRate;
    private long estimatedNetRevenue;
    private long cogs;
    private long grossProfit;
    private double profitMargin;
    private long avgProfitPerOrder;
    private List<Long> profitData;
    private long onlineRevenue;
    private long posRevenue;
    private long onlineOrders;
    private long posOrders;
    private long importCost;
    private long newCustomers;

    private List<String> revenueLabels;
    private List<Long> revenueData;

    private List<String> statusLabels;
    private List<Long> statusData;

    private List<String> channelLabels;
    private List<Long> channelData;

    private List<Map<String, Object>> topProducts;
    private List<Map<String, Object>> topCategories;
    private List<Map<String, Object>> topCustomers;
    private List<Map<String, Object>> orders;
}
