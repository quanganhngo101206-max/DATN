package com.skysport.datn.service;

import com.skysport.datn.dto.response.DashboardStatsDto;
import com.skysport.datn.dto.response.SalesReportDto;
import com.skysport.datn.entity.Bill;
import com.skysport.datn.entity.BillDetail;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.repository.BillDetailRepository;
import com.skysport.datn.repository.BillRepository;
import com.skysport.datn.repository.CustomerRepository;
import com.skysport.datn.repository.ImportOrderDetailRepository;
import com.skysport.datn.repository.ImportOrderRepository;
import com.skysport.datn.repository.ProductDetailRepository;
import com.skysport.datn.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private static final int POS_INVOICE_TYPE = 2;
    private static final int ONLINE_INVOICE_TYPE = 1;
    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final int COMPLETED_STATUS = OrderStatus.COMPLETED.getValue();
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DT_LABEL  = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Khoảng "all-time" mặc định cho dashboard: 5 năm gần nhất
    // Thay vì load không giới hạn, giới hạn ở mức hợp lý cho DATN.
    // Production: cân nhắc xem "tổng doanh thu" là từ ngày đầu kinh doanh hay theo năm tài chính.
    private static final int ALL_TIME_YEARS = 5;

    private final BillRepository billRepository;
    private final BillDetailRepository billDetailRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ProductDetailRepository productDetailRepository;
    private final ImportOrderRepository importOrderRepository;
    private final ImportOrderDetailRepository importOrderDetailRepository;

    public DashboardStatsDto getAdminDashboardStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd   = today.plusDays(1).atStartOfDay();

        // "All-time" thực dùng SUM(b.amount) không giới hạn ngày
        // — chỉ xảy ra 1 lần khi load dashboard, chấp nhận full-scan Bill
        long totalRevenue = nvl(billRepository.sumAmountByStatusAndDateRange(COMPLETED_STATUS, null, null));
        long todayRevenue = nvl(billRepository.sumAmountByStatusAndDateRange(COMPLETED_STATUS, todayStart, todayEnd));

        Map<Integer, Long> costMap = loadAverageImportCosts();

        // COGS all-time: dùng aggregation SQL thay vì load toàn bộ entity BillDetail
        // (không dùng findAllCompleted() — có thể load hàng triệu row)
        long totalCogs    = computeCogsViaSql(null, null, costMap);
        long grossProfit  = totalRevenue - totalCogs;

        long todayCogs    = computeCogsViaSql(todayStart, todayEnd, costMap);
        long todayProfit  = todayRevenue - todayCogs;

        double profitMargin = totalRevenue > 0 ? grossProfit * 100.0 / totalRevenue : 0;

        long todayOrders = nvl(billRepository.countByDateRange(todayStart, todayEnd));
        long totalOrders = billRepository.count();

        List<Map<String, Object>> lowStockList = buildLowStockList();
        LocalDate sevenDaysAgo = today.minusDays(6);

        return DashboardStatsDto.builder()
                .totalRevenue(totalRevenue)
                .todayRevenue(todayRevenue)
                .grossProfit(grossProfit)
                .todayProfit(todayProfit)
                .profitMargin(profitMargin)
                .totalOrders(totalOrders)
                .todayOrders(todayOrders)
                .totalProducts(productRepository.count())
                .totalCustomers(customerRepository.count())
                .pendingOrders(nvl(billRepository.countByStatusAndDateRange(
                        OrderStatus.PENDING.getValue(), null, null)))
                .shippingOrders(nvl(billRepository.countByStatusAndDateRange(
                        OrderStatus.SHIPPING.getValue(), null, null)))
                .lowStockCount(lowStockList.size())
                .revenueLabels(buildRevenueLabels(sevenDaysAgo, today))
                .revenueData(buildDailyRevenueSql(sevenDaysAgo, today))
                .profitData(buildDailyProfit(sevenDaysAgo, today, costMap))
                .statusLabels(statusLabels())
                .statusData(statusCounts(null, null))
                .topProducts(buildTopProductsSql(null, null, 5, costMap))
                .recentOrders(buildRecentOrders())
                .lowStockList(lowStockList)
                .build();
    }

    public DashboardStatsDto getStaffDashboardStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd   = today.plusDays(1).atStartOfDay();

        List<Map<String, Object>> lowStockList = buildLowStockList();

        return DashboardStatsDto.builder()
                .pendingOrders(nvl(billRepository.countByStatusAndDateRange(
                        OrderStatus.PENDING.getValue(), null, null)))
                .shippingOrders(nvl(billRepository.countByStatusAndDateRange(
                        OrderStatus.SHIPPING.getValue(), null, null)))
                .todayOrders(nvl(billRepository.countByDateRange(todayStart, todayEnd)))
                .todayRevenue(nvl(billRepository.sumAmountByStatusAndDateRange(
                        COMPLETED_STATUS, todayStart, todayEnd)))
                .recentOrders(buildRecentOrders())
                .lowStockList(lowStockList)
                .lowStockCount(lowStockList.size())
                .build();
    }

    public SalesReportDto getSalesReport(LocalDate fromDate, LocalDate toDate) {
        LocalDate start = fromDate != null ? fromDate : LocalDate.now().minusDays(29);
        LocalDate end   = toDate   != null ? toDate   : LocalDate.now();
        final LocalDate from = start.isAfter(end) ? end   : start;
        final LocalDate to   = start.isAfter(end) ? start : end;

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.plusDays(1).atStartOfDay();

        long revenue        = nvl(billRepository.sumAmountByStatusAndDateRange(COMPLETED_STATUS, fromDt, toDt));
        long completedCount = nvl(billRepository.countByStatusAndDateRange(COMPLETED_STATUS, fromDt, toDt));
        long cancelledCount = nvl(billRepository.countByStatusAndDateRange(OrderStatus.CANCELLED.getValue(), fromDt, toDt));
        long returningCount = nvl(billRepository.countByStatusAndDateRange(OrderStatus.RETURNING.getValue(), fromDt, toDt));
        long totalOrders    = billRepository.searchOrders(fromDt, toDt, null, null, null, Pageable.unpaged()).getTotalElements();

        long avgOrderValue    = completedCount > 0 ? revenue / completedCount : 0;
        double completionRate = totalOrders > 0 ? completedCount * 100.0 / totalOrders : 0;
        double cancellationRate = totalOrders > 0 ? cancelledCount * 100.0 / totalOrders : 0;

        // Online vs POS revenue — tách chính xác theo invoiceType
        long onlineRevenue = nvl(billRepository.sumAmountByStatusAndInvoiceTypeAndDateRange(
                COMPLETED_STATUS, ONLINE_INVOICE_TYPE, fromDt, toDt));
        long posRevenue    = nvl(billRepository.sumAmountByStatusAndInvoiceTypeAndDateRange(
                COMPLETED_STATUS, POS_INVOICE_TYPE, fromDt, toDt));
        long onlineOrders  = nvl(billRepository.countByStatusAndInvoiceTypeAndDateRange(
                COMPLETED_STATUS, ONLINE_INVOICE_TYPE, fromDt, toDt));
        long posOrders     = nvl(billRepository.countByStatusAndInvoiceTypeAndDateRange(
                COMPLETED_STATUS, POS_INVOICE_TYPE, fromDt, toDt));

        long importCost  = nvl(importOrderRepository.sumApprovedImportCost(fromDt, toDt));
        Map<Integer, Long> costMap = loadAverageImportCosts();

        // COGS trong range — dùng aggregation SQL để không load entity
        long cogs        = computeCogsViaSql(fromDt, toDt, costMap);
        long grossProfit = revenue - cogs;
        double profitMargin    = revenue > 0 ? grossProfit * 100.0 / revenue : 0;
        long avgProfitPerOrder = completedCount > 0 ? grossProfit / completedCount : 0;

        long newCustomers = customerRepository.countByAccount_CreateDateAfter(fromDt);

        return SalesReportDto.builder()
                .fromDate(from)
                .toDate(to)
                .revenue(revenue)
                .orderCount((int) totalOrders)
                .completedCount(completedCount)
                .cancelledCount(cancelledCount)
                .returningCount(returningCount)
                .avgOrderValue(avgOrderValue)
                .completionRate(completionRate)
                .cancellationRate(cancellationRate)
                .estimatedNetRevenue(revenue - importCost)
                .cogs(cogs)
                .grossProfit(grossProfit)
                .profitMargin(profitMargin)
                .avgProfitPerOrder(avgProfitPerOrder)
                .onlineRevenue(onlineRevenue)
                .posRevenue(posRevenue)
                .onlineOrders(onlineOrders)
                .posOrders(posOrders)
                .importCost(importCost)
                .newCustomers(newCustomers)
                .revenueLabels(buildRevenueLabels(from, to))
                .revenueData(buildDailyRevenueSql(from, to))
                .profitData(buildDailyProfit(from, to, costMap))
                .statusLabels(statusLabels())
                .statusData(statusCounts(fromDt, toDt))
                .channelLabels(List.of("Online", "Tại quầy"))
                .channelData(List.of(onlineRevenue, posRevenue))
                .topProducts(buildTopProductsSql(fromDt, toDt, 10, costMap))
                .topCategories(buildTopCategoriesSql(fromDt, toDt, 5))
                .topCustomers(buildTopCustomersSql(fromDt, toDt, 5))
                .orders(buildOrderRowsFromPage(fromDt, toDt))
                .build();
    }

    /**
     * Phân trang đơn hàng — dùng DB OFFSET/FETCH thay vì Java subList().
     */
    public Page<Map<String, Object>> getSalesOrderPage(
            LocalDate fromDate, LocalDate toDate,
            Integer status, String channel, String keyword,
            int page, int size) {

        LocalDate start = fromDate != null ? fromDate : LocalDate.now().minusDays(29);
        LocalDate end   = toDate   != null ? toDate   : LocalDate.now();
        final LocalDate from = start.isAfter(end) ? end   : start;
        final LocalDate to   = start.isAfter(end) ? start : end;

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.plusDays(1).atStartOfDay();

        int safeSize = List.of(5, 10, 20, 50).contains(size) ? size : 10;
        int safePage = Math.max(page, 0);

        Integer invoiceType = null;
        if ("pos".equalsIgnoreCase(channel)) {
            invoiceType = POS_INVOICE_TYPE;
        } else if ("online".equalsIgnoreCase(channel)) {
            invoiceType = ONLINE_INVOICE_TYPE;
        }

        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        Page<Bill> billPage = billRepository.searchOrders(
                fromDt, toDt,
                (status != null && status == 0) ? null : status,
                invoiceType,
                kw,
                PageRequest.of(safePage, safeSize));

        return billPage.map(this::toOrderRow);
    }

    public String exportSalesCsv(LocalDate fromDate, LocalDate toDate) {
        SalesReportDto report = getSalesReport(fromDate, toDate);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // UTF-8 BOM for Excel
        sb.append("Mã đơn,Khách hàng,Ngày tạo,Kênh,Trạng thái,Tổng tiền\n");
        for (Map<String, Object> row : report.getOrders()) {
            sb.append(csv(row.get("code"))).append(',')
                    .append(csv(row.get("customerName"))).append(',')
                    .append(csv(row.get("createDate"))).append(',')
                    .append(csv(row.get("channel"))).append(',')
                    .append(csv(row.get("statusText"))).append(',')
                    .append(row.get("amount")).append('\n');
        }
        return sb.toString();
    }

    public byte[] exportSalesExcel(
            LocalDate fromDate, LocalDate toDate,
            String type, Integer status, String channel, String keyword) throws IOException {

        SalesReportDto report = getSalesReport(fromDate, toDate);
        String exportType = type == null || type.isBlank() ? "all" : type.trim().toLowerCase();

        LocalDateTime fromDt = report.getFromDate().atStartOfDay();
        LocalDateTime toDt   = report.getToDate().plusDays(1).atStartOfDay();
        Map<Integer, Long> costMap = loadAverageImportCosts();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle moneyStyle  = createMoneyStyle(workbook);

            if ("all".equals(exportType) || "summary".equals(exportType)) {
                writeSummarySheet(workbook, report, headerStyle, moneyStyle);
            }
            if ("all".equals(exportType) || "products".equals(exportType)) {
                writeProductSheet(workbook,
                        buildTopProductsSql(fromDt, toDt, 100, costMap),
                        headerStyle, moneyStyle);
            }
            if ("all".equals(exportType) || "categories".equals(exportType)) {
                writeCategorySheet(workbook,
                        buildTopCategoriesSql(fromDt, toDt, 50),
                        headerStyle, moneyStyle);
            }
            if ("all".equals(exportType) || "customers".equals(exportType)) {
                writeCustomerSheet(workbook,
                        buildTopCustomersSql(fromDt, toDt, 50),
                        headerStyle, moneyStyle);
            }
            if ("all".equals(exportType) || "orders".equals(exportType)) {
                List<Map<String, Object>> orders = filterOrdersForExport(
                        report.getOrders(), status, channel, keyword);
                writeOrderSheet(workbook, orders, headerStyle, moneyStyle);
            }

            if (workbook.getNumberOfSheets() == 0) {
                writeSummarySheet(workbook, report, headerStyle, moneyStyle);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ===== SQL-backed builders =====

    private List<Long> buildDailyRevenueSql(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.plusDays(1).atStartOfDay();

        Map<LocalDate, Long> byDay = initDayMap(from, to);
        List<Object[]> rows = billRepository.sumAmountGroupByDate(COMPLETED_STATUS, fromDt, toDt);
        for (Object[] row : rows) {
            if (row[0] == null) continue;
            LocalDate d = (LocalDate) row[0];
            if (byDay.containsKey(d)) {
                byDay.put(d, ((Number) row[1]).longValue());
            }
        }
        return new ArrayList<>(byDay.values());
    }

    /**
     * Profit per ngày = revenue per ngày - COGS per ngày.
     *
     * COGS per ngày được tính qua aggregateDailyQtyByProductDetail():
     * trả về [date, productDetailId, qty_net] — đã GROUP BY trên DB,
     * không load entity BillDetail về Java.
     */
    private List<Long> buildDailyProfit(LocalDate from, LocalDate to, Map<Integer, Long> costMap) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.plusDays(1).atStartOfDay();

        Map<LocalDate, Long> revenueByDay = initDayMap(from, to);
        Map<LocalDate, Long> cogsByDay    = initDayMap(from, to);

        // Revenue per ngày từ SQL GROUP BY
        List<Object[]> revRows = billRepository.sumAmountGroupByDate(COMPLETED_STATUS, fromDt, toDt);
        for (Object[] row : revRows) {
            if (row[0] == null) continue;
            LocalDate d = (LocalDate) row[0];
            if (revenueByDay.containsKey(d)) {
                revenueByDay.put(d, ((Number) row[1]).longValue());
            }
        }

        // COGS per ngày từ SQL aggregation: [date, productDetailId, qty_net]
        // Không load BillDetail entity — chỉ tổng hợp (date, pdId, qty) trên DB
        List<Object[]> cogsRows = billDetailRepository.aggregateDailyQtyByProductDetail(fromDt, toDt);
        for (Object[] row : cogsRows) {
            if (row[0] == null || row[1] == null) continue;
            LocalDate d            = (LocalDate) row[0];
            Integer productDetailId = (Integer) row[1];
            long    qtyNet          = ((Number) row[2]).longValue();
            long    unitCost        = costMap.getOrDefault(productDetailId, 0L);
            if (cogsByDay.containsKey(d)) {
                cogsByDay.merge(d, unitCost * qtyNet, Long::sum);
            }
        }

        List<Long> result = new ArrayList<>();
        for (LocalDate d : revenueByDay.keySet()) {
            result.add(revenueByDay.get(d) - cogsByDay.getOrDefault(d, 0L));
        }
        return result;
    }

    /**
     * Top sản phẩm bán chạy — dùng SQL aggregation, không load entity.
     * Khi from=null: all-time (SQL scan Bill không giới hạn ngày).
     * costMap dùng để tính profit; nếu không có giá nhập thì profit = 0.
     */
    /**
     * costMap khóa theo productDetailId; ghép với dữ liệu bán ở mức productDetail rồi
     * cộng dồn lên theo sản phẩm để có cost/profit thật (thay vì cost=0 giả trước đây).
     */
    private List<Map<String, Object>> buildTopProductsSql(
            LocalDateTime from, LocalDateTime to, int limit, Map<Integer, Long> costMap) {

        // aggregateTopProducts trả: [productDetailId, productId, productName, totalSold, totalRevenue]
        List<Object[]> rows = billDetailRepository.aggregateTopProducts(from, to);

        // Gộp theo productId — một sản phẩm có thể có nhiều productDetail (size/màu khác nhau)
        Map<Integer, Map<String, Object>> byProduct = new LinkedHashMap<>();

        for (Object[] row : rows) {
            Integer productDetailId = row[0] != null ? ((Number) row[0]).intValue() : null;
            Integer productId       = row[1] != null ? ((Number) row[1]).intValue() : null;
            String name             = row[2] != null ? (String) row[2] : "?";
            long qty                = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            long rev                = row[4] != null ? ((Number) row[4]).longValue() : 0L;

            if (productId == null) continue;

            Long unitCost = productDetailId != null ? costMap.get(productDetailId) : null;
            long cost = unitCost != null ? unitCost * qty : 0L;

            Map<String, Object> acc = byProduct.computeIfAbsent(productId, id -> {
                Map<String, Object> m = new HashMap<>();
                m.put("productName", name);
                m.put("totalSold", 0L);
                m.put("revenue", 0L);
                m.put("cost", 0L);
                return m;
            });

            acc.put("totalSold", (Long) acc.get("totalSold") + qty);
            acc.put("revenue", (Long) acc.get("revenue") + rev);
            acc.put("cost", (Long) acc.get("cost") + cost);
        }

        return byProduct.values().stream()
                .peek(m -> {
                    long rev = (Long) m.get("revenue");
                    long cost = (Long) m.get("cost");
                    long profit = rev - cost;
                    double margin = rev > 0 ? profit * 100.0 / rev : 0;
                    m.put("profit", profit);
                    m.put("margin", margin);
                })
                .sorted((a, b) -> Long.compare((Long) b.get("totalSold"), (Long) a.get("totalSold")))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Top danh mục — dùng SQL aggregation, không load entity.
     */
    private List<Map<String, Object>> buildTopCategoriesSql(LocalDateTime from, LocalDateTime to, int limit) {
        List<Object[]> rows = billDetailRepository.aggregateTopCategories(from, to);
        return rows.stream()
                .limit(limit)
                .map(row -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("categoryName", row[1] != null ? row[1] : "-");
                    m.put("totalSold",    row[2] != null ? ((Number) row[2]).longValue() : 0L);
                    m.put("revenue",      row[3] != null ? ((Number) row[3]).longValue() : 0L);
                    return m;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildTopCustomersSql(LocalDateTime from, LocalDateTime to, int limit) {
        Pageable top = PageRequest.of(0, limit);
        List<Object[]> rows = billRepository.findTopCustomers(COMPLETED_STATUS, from, to, top);
        return rows.stream().map(row -> {
            Map<String, Object> m = new HashMap<>();
            m.put("customerId",   row[0]);
            m.put("customerName", row[1]);
            m.put("orderCount",   row[2]);
            m.put("revenue",      row[3]);
            return m;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildRecentOrders() {
        return billRepository.findTop5ByOrderByCreateDateDesc().stream()
                .map(this::toOrderRow)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildOrderRowsFromPage(LocalDateTime from, LocalDateTime to) {
        // Export: tối đa 1000 đơn để tránh OOM
        Page<Bill> page = billRepository.searchOrders(from, to, null, null, null,
                PageRequest.of(0, 1000));
        return page.getContent().stream().map(this::toOrderRow).collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildLowStockList() {
        Pageable top10 = PageRequest.of(0, 10);
        return productDetailRepository.findLowStock(LOW_STOCK_THRESHOLD, top10).stream()
                .map(pd -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productName", pd.getProduct() != null ? pd.getProduct().getName() : "-");
                    m.put("size",  pd.getSize()  != null ? pd.getSize().getName()  : "-");
                    m.put("color", pd.getColor() != null ? pd.getColor().getName() : "-");
                    m.put("quantity", pd.getQuantity());
                    return m;
                })
                .collect(Collectors.toList());
    }

    /**
     * Tính COGS bằng SQL aggregation [date, productDetailId, qtyNet] × costMap.
     * Thay thế hoàn toàn findAllCompleted() + findCompletedInRange() cho mục đích COGS.
     *
     * Khi from=null (all-time): SQL scan toàn bảng BillDetail — chấp nhận được
     * vì chỉ xảy ra ở dashboard admin, tần suất thấp.
     */
    private long computeCogsViaSql(LocalDateTime from, LocalDateTime to, Map<Integer, Long> costMap) {
        if (costMap.isEmpty()) return 0L;
        // Dùng aggregateDailyQtyByProductDetail để lấy [date, pdId, qty_net]
        // rồi tính × costMap — không load entity
        List<Object[]> rows = billDetailRepository.aggregateDailyQtyByProductDetail(from, to);
        long total = 0L;
        for (Object[] row : rows) {
            if (row[1] == null || row[2] == null) continue;
            Integer pdId  = (Integer) row[1];
            long    qty   = ((Number) row[2]).longValue();
            long    cost  = costMap.getOrDefault(pdId, 0L);
            total += cost * qty;
        }
        return total;
    }

    // ===== Helpers =====

    private Map<LocalDate, Long> initDayMap(LocalDate from, LocalDate to) {
        Map<LocalDate, Long> map = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            map.put(d, 0L);
            if (map.size() > 90) break;
        }
        return map;
    }

    private List<String> buildRevenueLabels(LocalDate from, LocalDate to) {
        List<String> labels = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            labels.add(d.format(DAY_LABEL));
            if (labels.size() > 90) break;
        }
        return labels;
    }

    private List<String> statusLabels() {
        return List.of(
                OrderStatus.PENDING.getLabel(),
                OrderStatus.CONFIRMED.getLabel(),
                OrderStatus.SHIPPING.getLabel(),
                OrderStatus.DELIVERED.getLabel(),
                OrderStatus.COMPLETED.getLabel(),
                OrderStatus.CANCELLED.getLabel(),
                OrderStatus.RETURNING.getLabel()
        );
    }

    private List<Long> statusCounts(LocalDateTime from, LocalDateTime to) {
        return List.of(
                nvl(billRepository.countByStatusAndDateRange(OrderStatus.PENDING.getValue(), from, to)),
                nvl(billRepository.countByStatusAndDateRange(OrderStatus.CONFIRMED.getValue(), from, to)),
                nvl(billRepository.countByStatusAndDateRange(OrderStatus.SHIPPING.getValue(), from, to)),
                nvl(billRepository.countByStatusAndDateRange(OrderStatus.DELIVERED.getValue(), from, to)),
                nvl(billRepository.countByStatusAndDateRange(OrderStatus.COMPLETED.getValue(), from, to)),
                nvl(billRepository.countByStatusAndDateRange(OrderStatus.CANCELLED.getValue(), from, to)),
                nvl(billRepository.countByStatusAndDateRange(OrderStatus.RETURNING.getValue(), from, to))
        );
    }

    private Map<String, Object> toOrderRow(Bill b) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",           b.getId());
        m.put("code",         b.getCode());
        m.put("customerName", b.getCustomer() != null ? b.getCustomer().getName() : "Khách lẻ");
        m.put("amount",       b.getAmount() != null ? b.getAmount().longValue() : 0L);
        m.put("status",       b.getStatus());
        m.put("statusText",   statusText(b.getStatus()));
        m.put("channel",      isPos(b) ? "Tại quầy" : "Online");
        m.put("createDate",   b.getCreateDate() != null
                ? b.getCreateDate().format(DT_LABEL) : "");
        return m;
    }

    private Map<Integer, Long> loadAverageImportCosts() {
        Map<Integer, Long> costs = new HashMap<>();
        for (Object[] row : importOrderDetailRepository.findAverageImportPriceByProductDetail()) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) continue;
            costs.put((Integer) row[0], ((Number) row[1]).longValue());
        }
        return costs;
    }

    private boolean isPos(Bill b) {
        return Objects.equals(b.getInvoiceType(), POS_INVOICE_TYPE);
    }

    private String statusText(Integer status) {
        OrderStatus os = OrderStatus.of(status);
        return os != null ? os.getLabel() : "Không xác định";
    }

    private long nvl(Long value) {
        return value != null ? value : 0L;
    }

    private List<Map<String, Object>> filterOrdersForExport(
            List<Map<String, Object>> orders, Integer status, String channel, String keyword) {
        final String ch = channel == null ? "" : channel.trim().toLowerCase();
        final String kw = keyword  == null ? "" : keyword.trim().toLowerCase();
        return orders.stream()
                .filter(o -> status == null || status == 0 || Objects.equals(o.get("status"), status))
                .filter(o -> {
                    if (ch.isEmpty()) return true;
                    String c = String.valueOf(o.getOrDefault("channel", "")).toLowerCase();
                    if ("pos".equals(ch))    return c.contains("quầy") || c.contains("pos");
                    if ("online".equals(ch)) return c.contains("online");
                    return true;
                })
                .filter(o -> {
                    if (kw.isEmpty()) return true;
                    String code = String.valueOf(o.getOrDefault("code", "")).toLowerCase();
                    String cust = String.valueOf(o.getOrDefault("customerName", "")).toLowerCase();
                    return code.contains(kw) || cust.contains(kw);
                })
                .collect(Collectors.toList());
    }

    // ===== Excel writers =====

    private void writeSummarySheet(Workbook wb, SalesReportDto r, CellStyle hStyle, CellStyle mStyle) {
        Sheet sheet = wb.createSheet("Tong quan");
        Row title = sheet.createRow(0);
        Cell tc = title.createCell(0);
        tc.setCellValue("Báo cáo thống kê SkySport");
        tc.setCellStyle(hStyle);

        Row period = sheet.createRow(1);
        period.createCell(0).setCellValue("Từ ngày");
        period.createCell(1).setCellValue(String.valueOf(r.getFromDate()));
        period.createCell(2).setCellValue("Đến ngày");
        period.createCell(3).setCellValue(String.valueOf(r.getToDate()));

        String[][] kpis = {
                {"Doanh thu hoàn thành", String.valueOf(r.getRevenue())},
                {"Tổng đơn", String.valueOf(r.getOrderCount())},
                {"Đơn hoàn thành", String.valueOf(r.getCompletedCount())},
                {"Đơn hủy", String.valueOf(r.getCancelledCount())},
                {"Đơn trả hàng", String.valueOf(r.getReturningCount())},
                {"Giá trị đơn TB", String.valueOf(r.getAvgOrderValue())},
                {"Tỷ lệ hoàn thành (%)", String.format("%.1f", r.getCompletionRate())},
                {"Tỷ lệ hủy (%)", String.format("%.1f", r.getCancellationRate())},
                {"Doanh thu Online", String.valueOf(r.getOnlineRevenue())},
                {"Doanh thu tại quầy", String.valueOf(r.getPosRevenue())},
                {"Chi phí nhập hàng", String.valueOf(r.getImportCost())},
                {"Giá vốn hàng bán (COGS)", String.valueOf(r.getCogs())},
                {"Lời (doanh thu - giá vốn)", String.valueOf(r.getGrossProfit())},
                {"Tỷ suất lãi (%)", String.format("%.1f", r.getProfitMargin())},
                {"Lời TB / đơn", String.valueOf(r.getAvgProfitPerOrder())},
                {"Chênh lệch thu - chi", String.valueOf(r.getEstimatedNetRevenue())},
                {"Khách mới", String.valueOf(r.getNewCustomers())}
        };

        Row header = sheet.createRow(3);
        Cell h0 = header.createCell(0); h0.setCellValue("Chỉ số");  h0.setCellStyle(hStyle);
        Cell h1 = header.createCell(1); h1.setCellValue("Giá trị"); h1.setCellStyle(hStyle);

        for (int i = 0; i < kpis.length; i++) {
            Row row = sheet.createRow(4 + i);
            row.createCell(0).setCellValue(kpis[i][0]);
            Cell vc = row.createCell(1);
            try {
                double number = Double.parseDouble(kpis[i][1]);
                vc.setCellValue(number);
                if (kpis[i][0].contains("Doanh thu") || kpis[i][0].contains("Giá trị")
                        || kpis[i][0].contains("Chi phí") || kpis[i][0].contains("Giá vốn")
                        || kpis[i][0].contains("Lời") || kpis[i][0].contains("Chênh lệch")) {
                    vc.setCellStyle(mStyle);
                }
            } catch (NumberFormatException ex) {
                vc.setCellValue(kpis[i][1]);
            }
        }
        autosize(sheet, 2);
    }

    private void writeProductSheet(Workbook wb, List<Map<String, Object>> products, CellStyle hStyle, CellStyle mStyle) {
        Sheet sheet = wb.createSheet("Top san pham");
        writeHeader(sheet, hStyle, "#", "Sản phẩm", "Đã bán", "Doanh thu", "Giá vốn", "Lời", "Lãi (%)");
        int r = 1;
        for (Map<String, Object> p : products) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(r - 1);
            row.createCell(1).setCellValue(str(p.get("productName")));
            row.createCell(2).setCellValue(toLong(p.get("totalSold")));
            money(row.createCell(3), p.get("revenue"), mStyle);
            money(row.createCell(4), p.get("cost"), mStyle);
            money(row.createCell(5), p.get("profit"), mStyle);
            row.createCell(6).setCellValue(toDouble(p.get("margin")));
        }
        autosize(sheet, 7);
    }

    private void writeCategorySheet(Workbook wb, List<Map<String, Object>> cats, CellStyle hStyle, CellStyle mStyle) {
        Sheet sheet = wb.createSheet("Top danh muc");
        writeHeader(sheet, hStyle, "Danh mục", "Số lượng", "Doanh thu");
        int r = 1;
        for (Map<String, Object> c : cats) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(str(c.get("categoryName")));
            row.createCell(1).setCellValue(toLong(c.get("totalSold")));
            money(row.createCell(2), c.get("revenue"), mStyle);
        }
        autosize(sheet, 3);
    }

    private void writeCustomerSheet(Workbook wb, List<Map<String, Object>> custs, CellStyle hStyle, CellStyle mStyle) {
        Sheet sheet = wb.createSheet("Top khach hang");
        writeHeader(sheet, hStyle, "Khách hàng", "Đơn hoàn thành", "Doanh thu");
        int r = 1;
        for (Map<String, Object> c : custs) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(str(c.get("customerName")));
            row.createCell(1).setCellValue(toLong(c.get("orderCount")));
            money(row.createCell(2), c.get("revenue"), mStyle);
        }
        autosize(sheet, 3);
    }

    private void writeOrderSheet(Workbook wb, List<Map<String, Object>> orders, CellStyle hStyle, CellStyle mStyle) {
        Sheet sheet = wb.createSheet("Don hang");
        writeHeader(sheet, hStyle, "Mã đơn", "Khách hàng", "Ngày tạo", "Kênh", "Trạng thái", "Tổng tiền");
        int r = 1;
        for (Map<String, Object> o : orders) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(str(o.get("code")));
            row.createCell(1).setCellValue(str(o.get("customerName")));
            row.createCell(2).setCellValue(str(o.get("createDate")));
            row.createCell(3).setCellValue(str(o.get("channel")));
            row.createCell(4).setCellValue(str(o.get("statusText")));
            money(row.createCell(5), o.get("amount"), mStyle);
        }
        autosize(sheet, 6);
    }

    private void writeHeader(Sheet sheet, CellStyle hStyle, String... headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(hStyle);
        }
    }

    private void money(Cell cell, Object value, CellStyle style) {
        cell.setCellValue(toLong(value));
        cell.setCellStyle(style);
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createMoneyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
        return style;
    }

    private void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) sheet.autoSizeColumn(i);
    }

    private String str(Object value)  { return value == null ? "" : String.valueOf(value); }

    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ex) { return 0L; }
    }

    private double toDouble(Object value) {
        if (value == null) return 0d;
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException ex) { return 0d; }
    }

    private String csv(Object value) {
        String s = value == null ? "" : value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}