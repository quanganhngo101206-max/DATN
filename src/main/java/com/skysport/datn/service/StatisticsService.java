package com.skysport.datn.service;

import com.skysport.datn.dto.response.DashboardStatsDto;
import com.skysport.datn.dto.response.SalesReportDto;
import com.skysport.datn.entity.Bill;
import com.skysport.datn.entity.BillDetail;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.repository.BillRepository;
import com.skysport.datn.repository.CustomerRepository;
import com.skysport.datn.repository.ImportOrderRepository;
import com.skysport.datn.repository.ProductDetailRepository;
import com.skysport.datn.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private static final int POS_INVOICE_TYPE = 2;
    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");

    private final BillRepository billRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ProductDetailRepository productDetailRepository;
    private final ImportOrderRepository importOrderRepository;

    public DashboardStatsDto getAdminDashboardStats() {
        List<Bill> allBills = billRepository.findAll();
        LocalDate today = LocalDate.now();

        long totalRevenue = sumCompletedRevenue(allBills, null, null);
        long todayRevenue = sumCompletedRevenue(allBills, today, today);
        long todayOrders = allBills.stream()
                .filter(b -> isOnDate(b.getCreateDate(), today))
                .count();

        List<Map<String, Object>> lowStockList = buildLowStockList();

        return DashboardStatsDto.builder()
                .totalRevenue(totalRevenue)
                .todayRevenue(todayRevenue)
                .totalOrders(allBills.size())
                .todayOrders(todayOrders)
                .totalProducts(productRepository.count())
                .totalCustomers(customerRepository.count())
                .pendingOrders(countByStatus(allBills, OrderStatus.PENDING))
                .shippingOrders(countByStatus(allBills, OrderStatus.SHIPPING))
                .lowStockCount(lowStockList.size())
                .revenueLabels(buildRevenueLabels(today.minusDays(6), today))
                .revenueData(buildDailyRevenue(allBills, today.minusDays(6), today))
                .statusLabels(List.of(
                        OrderStatus.PENDING.getLabel(),
                        OrderStatus.CONFIRMED.getLabel(),
                        OrderStatus.SHIPPING.getLabel(),
                        OrderStatus.DELIVERED.getLabel(),
                        OrderStatus.COMPLETED.getLabel(),
                        OrderStatus.CANCELLED.getLabel(),
                        OrderStatus.RETURNING.getLabel()
                ))
                .statusData(List.of(
                        countByStatus(allBills, OrderStatus.PENDING),
                        countByStatus(allBills, OrderStatus.CONFIRMED),
                        countByStatus(allBills, OrderStatus.SHIPPING),
                        countByStatus(allBills, OrderStatus.DELIVERED),
                        countByStatus(allBills, OrderStatus.COMPLETED),
                        countByStatus(allBills, OrderStatus.CANCELLED),
                        countByStatus(allBills, OrderStatus.RETURNING)
                ))
                .topProducts(buildTopProducts(allBills, null, null, 5))
                .recentOrders(buildRecentOrders(allBills, 5))
                .lowStockList(lowStockList)
                .build();
    }

    public DashboardStatsDto getStaffDashboardStats() {
        List<Bill> allBills = billRepository.findAll();
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> lowStockList = buildLowStockList();

        return DashboardStatsDto.builder()
                .pendingOrders(countByStatus(allBills, OrderStatus.PENDING))
                .shippingOrders(countByStatus(allBills, OrderStatus.SHIPPING))
                .todayOrders(allBills.stream().filter(b -> isOnDate(b.getCreateDate(), today)).count())
                .todayRevenue(sumCompletedRevenue(allBills, today, today))
                .recentOrders(buildRecentOrders(allBills, 5))
                .lowStockList(lowStockList)
                .lowStockCount(lowStockList.size())
                .build();
    }

    public SalesReportDto getSalesReport(LocalDate fromDate, LocalDate toDate) {
        LocalDate start = fromDate != null ? fromDate : LocalDate.now().minusDays(29);
        LocalDate end = toDate != null ? toDate : LocalDate.now();
        final LocalDate from = start.isAfter(end) ? end : start;
        final LocalDate to = start.isAfter(end) ? start : end;

        List<Bill> allBills = billRepository.findAll();
        List<Bill> periodBills = filterByDateRange(allBills, from, to);
        List<Bill> completed = periodBills.stream()
                .filter(b -> OrderStatus.COMPLETED.matches(b.getStatus()))
                .collect(Collectors.toList());

        long revenue = sumCompletedRevenue(periodBills, from, to);
        long completedCount = completed.size();
        long cancelledCount = countByStatus(periodBills, OrderStatus.CANCELLED);
        long returningCount = countByStatus(periodBills, OrderStatus.RETURNING);
        long avgOrderValue = completedCount > 0 ? revenue / completedCount : 0;
        double completionRate = periodBills.isEmpty()
                ? 0 : completedCount * 100.0 / periodBills.size();
        double cancellationRate = periodBills.isEmpty()
                ? 0 : cancelledCount * 100.0 / periodBills.size();

        long onlineRevenue = 0;
        long posRevenue = 0;
        long onlineOrders = 0;
        long posOrders = 0;
        for (Bill b : completed) {
            long amount = amountOf(b);
            if (isPos(b)) {
                posRevenue += amount;
                posOrders++;
            } else {
                onlineRevenue += amount;
                onlineOrders++;
            }
        }

        long importCost = importOrderRepository.findAll().stream()
                .filter(io -> io.getCreateDate() != null
                        && !io.getCreateDate().toLocalDate().isBefore(from)
                        && !io.getCreateDate().toLocalDate().isAfter(to))
                .filter(io -> io.getStatus() != null && io.getStatus() == 2)
                .mapToLong(io -> io.getTotalAmount() != null ? io.getTotalAmount().longValue() : 0L)
                .sum();

        long newCustomers = customerRepository.findAll().stream()
                .filter(c -> c.getAccount() != null && c.getAccount().getCreateDate() != null)
                .filter(c -> {
                    LocalDate d = c.getAccount().getCreateDate().toLocalDate();
                    return !d.isBefore(from) && !d.isAfter(to);
                })
                .count();

        return SalesReportDto.builder()
                .fromDate(from)
                .toDate(to)
                .revenue(revenue)
                .orderCount(periodBills.size())
                .completedCount(completedCount)
                .cancelledCount(cancelledCount)
                .returningCount(returningCount)
                .avgOrderValue(avgOrderValue)
                .completionRate(completionRate)
                .cancellationRate(cancellationRate)
                .estimatedNetRevenue(revenue - importCost)
                .onlineRevenue(onlineRevenue)
                .posRevenue(posRevenue)
                .onlineOrders(onlineOrders)
                .posOrders(posOrders)
                .importCost(importCost)
                .newCustomers(newCustomers)
                .revenueLabels(buildRevenueLabels(from, to))
                .revenueData(buildDailyRevenue(allBills, from, to))
                .statusLabels(List.of(
                        OrderStatus.PENDING.getLabel(),
                        OrderStatus.CONFIRMED.getLabel(),
                        OrderStatus.SHIPPING.getLabel(),
                        OrderStatus.DELIVERED.getLabel(),
                        OrderStatus.COMPLETED.getLabel(),
                        OrderStatus.CANCELLED.getLabel(),
                        OrderStatus.RETURNING.getLabel()
                ))
                .statusData(List.of(
                        countByStatus(periodBills, OrderStatus.PENDING),
                        countByStatus(periodBills, OrderStatus.CONFIRMED),
                        countByStatus(periodBills, OrderStatus.SHIPPING),
                        countByStatus(periodBills, OrderStatus.DELIVERED),
                        countByStatus(periodBills, OrderStatus.COMPLETED),
                        countByStatus(periodBills, OrderStatus.CANCELLED),
                        countByStatus(periodBills, OrderStatus.RETURNING)
                ))
                .channelLabels(List.of("Online", "Tại quầy"))
                .channelData(List.of(onlineRevenue, posRevenue))
                .topProducts(buildTopProducts(periodBills, from, to, 10))
                .topCategories(buildTopCategories(periodBills, 5))
                .topCustomers(buildTopCustomers(periodBills, 5))
                .orders(buildOrderRows(periodBills, Integer.MAX_VALUE))
                .build();
    }

    public Page<Map<String, Object>> getSalesOrderPage(
            LocalDate fromDate,
            LocalDate toDate,
            Integer status,
            String channel,
            String keyword,
            int page,
            int size) {

        LocalDate start = fromDate != null ? fromDate : LocalDate.now().minusDays(29);
        LocalDate end = toDate != null ? toDate : LocalDate.now();
        final LocalDate from = start.isAfter(end) ? end : start;
        final LocalDate to = start.isAfter(end) ? start : end;
        final String normalizedChannel = channel == null ? "" : channel.trim().toLowerCase();
        final String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();

        List<Bill> filtered = filterByDateRange(billRepository.findAll(), from, to).stream()
                .filter(b -> status == null || status == 0 || Objects.equals(b.getStatus(), status))
                .filter(b -> normalizedChannel.isEmpty()
                        || ("pos".equals(normalizedChannel) && isPos(b))
                        || ("online".equals(normalizedChannel) && !isPos(b)))
                .filter(b -> {
                    if (normalizedKeyword.isEmpty()) return true;
                    String code = b.getCode() != null ? b.getCode().toLowerCase() : "";
                    String customer = b.getCustomer() != null && b.getCustomer().getName() != null
                            ? b.getCustomer().getName().toLowerCase() : "khách lẻ";
                    return code.contains(normalizedKeyword) || customer.contains(normalizedKeyword);
                })
                .sorted(Comparator.comparing(
                        Bill::getCreateDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        int safeSize = List.of(5, 10, 20, 50).contains(size) ? size : 10;
        int totalPages = (int) Math.ceil(filtered.size() / (double) safeSize);
        int safePage = Math.min(Math.max(page, 0), Math.max(totalPages - 1, 0));
        int fromIndex = safePage * safeSize;
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());

        List<Map<String, Object>> content = filtered.subList(fromIndex, toIndex).stream()
                .map(this::toOrderRow)
                .collect(Collectors.toList());

        return new PageImpl<>(
                content,
                PageRequest.of(safePage, safeSize),
                filtered.size());
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

    /**
     * Xuất Excel (.xlsx) cho các bảng thống kê.
     * type: all | products | categories | customers | orders
     */
    public byte[] exportSalesExcel(
            LocalDate fromDate,
            LocalDate toDate,
            String type,
            Integer status,
            String channel,
            String keyword) throws IOException {

        SalesReportDto report = getSalesReport(fromDate, toDate);
        String exportType = type == null || type.isBlank() ? "all" : type.trim().toLowerCase();
        List<Bill> periodBills = filterByDateRange(
                billRepository.findAll(), report.getFromDate(), report.getToDate());

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle moneyStyle = createMoneyStyle(workbook);

            if ("all".equals(exportType) || "summary".equals(exportType)) {
                writeSummarySheet(workbook, report, headerStyle, moneyStyle);
            }
            if ("all".equals(exportType) || "products".equals(exportType)) {
                writeProductSheet(
                        workbook,
                        buildTopProducts(periodBills, report.getFromDate(), report.getToDate(), 100),
                        headerStyle,
                        moneyStyle);
            }
            if ("all".equals(exportType) || "categories".equals(exportType)) {
                writeCategorySheet(
                        workbook,
                        buildTopCategories(periodBills, 50),
                        headerStyle,
                        moneyStyle);
            }
            if ("all".equals(exportType) || "customers".equals(exportType)) {
                writeCustomerSheet(
                        workbook,
                        buildTopCustomers(periodBills, 50),
                        headerStyle,
                        moneyStyle);
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

    private List<Map<String, Object>> filterOrdersForExport(
            List<Map<String, Object>> orders,
            Integer status,
            String channel,
            String keyword) {
        final String normalizedChannel = channel == null ? "" : channel.trim().toLowerCase();
        final String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();

        return orders.stream()
                .filter(o -> status == null || status == 0
                        || Objects.equals(o.get("status"), status))
                .filter(o -> {
                    if (normalizedChannel.isEmpty()) return true;
                    String ch = String.valueOf(o.getOrDefault("channel", "")).toLowerCase();
                    if ("pos".equals(normalizedChannel)) return ch.contains("quầy") || ch.contains("pos");
                    if ("online".equals(normalizedChannel)) return ch.contains("online");
                    return true;
                })
                .filter(o -> {
                    if (normalizedKeyword.isEmpty()) return true;
                    String code = String.valueOf(o.getOrDefault("code", "")).toLowerCase();
                    String customer = String.valueOf(o.getOrDefault("customerName", "")).toLowerCase();
                    return code.contains(normalizedKeyword) || customer.contains(normalizedKeyword);
                })
                .collect(Collectors.toList());
    }

    private void writeSummarySheet(
            Workbook workbook,
            SalesReportDto report,
            CellStyle headerStyle,
            CellStyle moneyStyle) {
        Sheet sheet = workbook.createSheet("Tong quan");
        Row title = sheet.createRow(0);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("Báo cáo thống kê SkySport");
        titleCell.setCellStyle(headerStyle);

        Row period = sheet.createRow(1);
        period.createCell(0).setCellValue("Từ ngày");
        period.createCell(1).setCellValue(String.valueOf(report.getFromDate()));
        period.createCell(2).setCellValue("Đến ngày");
        period.createCell(3).setCellValue(String.valueOf(report.getToDate()));

        String[][] kpis = {
                {"Doanh thu hoàn thành", String.valueOf(report.getRevenue())},
                {"Tổng đơn", String.valueOf(report.getOrderCount())},
                {"Đơn hoàn thành", String.valueOf(report.getCompletedCount())},
                {"Đơn hủy", String.valueOf(report.getCancelledCount())},
                {"Đơn trả hàng", String.valueOf(report.getReturningCount())},
                {"Giá trị đơn TB", String.valueOf(report.getAvgOrderValue())},
                {"Tỷ lệ hoàn thành (%)", String.format("%.1f", report.getCompletionRate())},
                {"Tỷ lệ hủy (%)", String.format("%.1f", report.getCancellationRate())},
                {"Doanh thu Online", String.valueOf(report.getOnlineRevenue())},
                {"Doanh thu tại quầy", String.valueOf(report.getPosRevenue())},
                {"Chi phí nhập hàng", String.valueOf(report.getImportCost())},
                {"Chênh lệch thu - chi", String.valueOf(report.getEstimatedNetRevenue())},
                {"Khách mới", String.valueOf(report.getNewCustomers())}
        };

        Row header = sheet.createRow(3);
        Cell h0 = header.createCell(0);
        h0.setCellValue("Chỉ số");
        h0.setCellStyle(headerStyle);
        Cell h1 = header.createCell(1);
        h1.setCellValue("Giá trị");
        h1.setCellStyle(headerStyle);

        for (int i = 0; i < kpis.length; i++) {
            Row row = sheet.createRow(4 + i);
            row.createCell(0).setCellValue(kpis[i][0]);
            Cell valueCell = row.createCell(1);
            try {
                double number = Double.parseDouble(kpis[i][1]);
                valueCell.setCellValue(number);
                if (kpis[i][0].contains("Doanh thu")
                        || kpis[i][0].contains("Giá trị")
                        || kpis[i][0].contains("Chi phí")
                        || kpis[i][0].contains("Chênh lệch")) {
                    valueCell.setCellStyle(moneyStyle);
                }
            } catch (NumberFormatException ex) {
                valueCell.setCellValue(kpis[i][1]);
            }
        }
        autosize(sheet, 2);
    }

    private void writeProductSheet(
            Workbook workbook,
            List<Map<String, Object>> products,
            CellStyle headerStyle,
            CellStyle moneyStyle) {
        Sheet sheet = workbook.createSheet("Top san pham");
        writeHeader(sheet, headerStyle, "#", "Sản phẩm", "Đã bán", "Doanh thu");
        int rowIdx = 1;
        for (Map<String, Object> p : products) {
            Row row = sheet.createRow(rowIdx);
            row.createCell(0).setCellValue(rowIdx);
            row.createCell(1).setCellValue(str(p.get("productName")));
            row.createCell(2).setCellValue(toLong(p.get("totalSold")));
            Cell rev = row.createCell(3);
            rev.setCellValue(toLong(p.get("revenue")));
            rev.setCellStyle(moneyStyle);
            rowIdx++;
        }
        autosize(sheet, 4);
    }

    private void writeCategorySheet(
            Workbook workbook,
            List<Map<String, Object>> categories,
            CellStyle headerStyle,
            CellStyle moneyStyle) {
        Sheet sheet = workbook.createSheet("Top danh muc");
        writeHeader(sheet, headerStyle, "Danh mục", "Số lượng", "Doanh thu");
        int rowIdx = 1;
        for (Map<String, Object> c : categories) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(str(c.get("categoryName")));
            row.createCell(1).setCellValue(toLong(c.get("totalSold")));
            Cell rev = row.createCell(2);
            rev.setCellValue(toLong(c.get("revenue")));
            rev.setCellStyle(moneyStyle);
        }
        autosize(sheet, 3);
    }

    private void writeCustomerSheet(
            Workbook workbook,
            List<Map<String, Object>> customers,
            CellStyle headerStyle,
            CellStyle moneyStyle) {
        Sheet sheet = workbook.createSheet("Top khach hang");
        writeHeader(sheet, headerStyle, "Khách hàng", "Đơn hoàn thành", "Doanh thu");
        int rowIdx = 1;
        for (Map<String, Object> c : customers) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(str(c.get("customerName")));
            row.createCell(1).setCellValue(toLong(c.get("orderCount")));
            Cell rev = row.createCell(2);
            rev.setCellValue(toLong(c.get("revenue")));
            rev.setCellStyle(moneyStyle);
        }
        autosize(sheet, 3);
    }

    private void writeOrderSheet(
            Workbook workbook,
            List<Map<String, Object>> orders,
            CellStyle headerStyle,
            CellStyle moneyStyle) {
        Sheet sheet = workbook.createSheet("Don hang");
        writeHeader(sheet, headerStyle, "Mã đơn", "Khách hàng", "Ngày tạo", "Kênh", "Trạng thái", "Tổng tiền");
        int rowIdx = 1;
        for (Map<String, Object> o : orders) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(str(o.get("code")));
            row.createCell(1).setCellValue(str(o.get("customerName")));
            row.createCell(2).setCellValue(str(o.get("createDate")));
            row.createCell(3).setCellValue(str(o.get("channel")));
            row.createCell(4).setCellValue(str(o.get("statusText")));
            Cell amount = row.createCell(5);
            amount.setCellValue(toLong(o.get("amount")));
            amount.setCellStyle(moneyStyle);
        }
        autosize(sheet, 6);
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle, String... headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
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

    private CellStyle createMoneyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        return style;
    }

    private void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    // ===== helpers =====

    private List<Bill> filterByDateRange(List<Bill> bills, LocalDate from, LocalDate to) {
        return bills.stream()
                .filter(b -> b.getCreateDate() != null)
                .filter(b -> {
                    LocalDate d = b.getCreateDate().toLocalDate();
                    return !d.isBefore(from) && !d.isAfter(to);
                })
                .collect(Collectors.toList());
    }

    private long sumCompletedRevenue(List<Bill> bills, LocalDate from, LocalDate to) {
        return bills.stream()
                .filter(b -> OrderStatus.COMPLETED.matches(b.getStatus()))
                .filter(b -> {
                    if (from == null && to == null) return true;
                    if (b.getCreateDate() == null) return false;
                    LocalDate d = b.getCreateDate().toLocalDate();
                    if (from != null && d.isBefore(from)) return false;
                    if (to != null && d.isAfter(to)) return false;
                    return true;
                })
                .mapToLong(this::amountOf)
                .sum();
    }

    private List<String> buildRevenueLabels(LocalDate from, LocalDate to) {
        List<String> labels = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            labels.add(d.format(DAY_LABEL));
            // Cap chart points for very long ranges (keep all labels for short ranges)
            if (labels.size() > 90) break;
        }
        return labels;
    }

    private List<Long> buildDailyRevenue(List<Bill> bills, LocalDate from, LocalDate to) {
        Map<LocalDate, Long> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDay.put(d, 0L);
            if (byDay.size() > 90) break;
        }
        bills.stream()
                .filter(b -> OrderStatus.COMPLETED.matches(b.getStatus()))
                .filter(b -> b.getCreateDate() != null)
                .forEach(b -> {
                    LocalDate d = b.getCreateDate().toLocalDate();
                    if (byDay.containsKey(d)) {
                        byDay.merge(d, amountOf(b), Long::sum);
                    }
                });
        return new ArrayList<>(byDay.values());
    }

    private List<Map<String, Object>> buildTopProducts(List<Bill> bills, LocalDate from, LocalDate to, int limit) {
        Map<String, long[]> productStats = new LinkedHashMap<>();

        bills.stream()
                .filter(b -> OrderStatus.COMPLETED.matches(b.getStatus()))
                .filter(b -> {
                    if (from == null && to == null) return true;
                    if (b.getCreateDate() == null) return false;
                    LocalDate d = b.getCreateDate().toLocalDate();
                    if (from != null && d.isBefore(from)) return false;
                    if (to != null && d.isAfter(to)) return false;
                    return true;
                })
                .forEach(bill -> {
                    if (bill.getBillDetails() == null) return;
                    for (BillDetail detail : bill.getBillDetails()) {
                        if (detail.getProductDetail() == null || detail.getProductDetail().getProduct() == null) {
                            continue;
                        }
                        String name = detail.getProductDetail().getProduct().getName();
                        long qty = detail.getQuantity() != null ? detail.getQuantity() : 0;
                        long rev = detail.getMomentPrice() != null
                                ? (long) (detail.getMomentPrice() * qty) : 0;
                        productStats.merge(name, new long[]{qty, rev},
                                (a, b) -> new long[]{a[0] + b[0], a[1] + b[1]});
                    }
                });

        return productStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productName", e.getKey());
                    m.put("totalSold", e.getValue()[0]);
                    m.put("revenue", e.getValue()[1]);
                    return m;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildTopCategories(List<Bill> bills, int limit) {
        Map<String, long[]> catStats = new LinkedHashMap<>();

        bills.stream()
                .filter(b -> OrderStatus.COMPLETED.matches(b.getStatus()))
                .forEach(bill -> {
                    if (bill.getBillDetails() == null) return;
                    for (BillDetail detail : bill.getBillDetails()) {
                        if (detail.getProductDetail() == null
                                || detail.getProductDetail().getProduct() == null
                                || detail.getProductDetail().getProduct().getCategory() == null) {
                            continue;
                        }
                        String name = detail.getProductDetail().getProduct().getCategory().getName();
                        long qty = detail.getQuantity() != null ? detail.getQuantity() : 0;
                        long rev = detail.getMomentPrice() != null
                                ? (long) (detail.getMomentPrice() * qty) : 0;
                        catStats.merge(name, new long[]{qty, rev},
                                (a, b) -> new long[]{a[0] + b[0], a[1] + b[1]});
                    }
                });

        return catStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("categoryName", e.getKey());
                    m.put("totalSold", e.getValue()[0]);
                    m.put("revenue", e.getValue()[1]);
                    return m;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildTopCustomers(List<Bill> bills, int limit) {
        Map<String, long[]> customerStats = new LinkedHashMap<>();

        bills.stream()
                .filter(b -> OrderStatus.COMPLETED.matches(b.getStatus()))
                .forEach(b -> {
                    String name = b.getCustomer() != null && b.getCustomer().getName() != null
                            ? b.getCustomer().getName() : "Khách lẻ";
                    customerStats.merge(name, new long[]{1, amountOf(b)},
                            (a, value) -> new long[]{a[0] + value[0], a[1] + value[1]});
                });

        return customerStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("customerName", e.getKey());
                    m.put("orderCount", e.getValue()[0]);
                    m.put("revenue", e.getValue()[1]);
                    return m;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildRecentOrders(List<Bill> bills, int limit) {
        return bills.stream()
                .sorted(Comparator.comparing(Bill::getCreateDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(this::toOrderRow)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildOrderRows(List<Bill> bills, int limit) {
        return bills.stream()
                .sorted(Comparator.comparing(Bill::getCreateDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(this::toOrderRow)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toOrderRow(Bill b) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", b.getId());
        m.put("code", b.getCode());
        m.put("customerName", b.getCustomer() != null ? b.getCustomer().getName() : "Khách lẻ");
        m.put("amount", amountOf(b));
        m.put("status", b.getStatus());
        m.put("statusText", statusText(b.getStatus()));
        m.put("channel", isPos(b) ? "Tại quầy" : "Online");
        m.put("createDate", b.getCreateDate() != null
                ? b.getCreateDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                : "");
        return m;
    }

    private List<Map<String, Object>> buildLowStockList() {
        return productDetailRepository.findAll().stream()
                .filter(pd -> pd.getDeleteFlag() == null || !pd.getDeleteFlag())
                .filter(pd -> pd.getQuantity() != null
                        && pd.getQuantity() > 0
                        && pd.getQuantity() <= LOW_STOCK_THRESHOLD)
                .sorted(Comparator.comparing(pd -> pd.getQuantity() != null ? pd.getQuantity() : 0))
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
    }

    private long countByStatus(List<Bill> bills, OrderStatus status) {
        return bills.stream()
                .filter(b -> status.matches(b.getStatus()))
                .count();
    }

    private long amountOf(Bill b) {
        return b.getAmount() != null ? b.getAmount().longValue() : 0L;
    }

    private boolean isPos(Bill b) {
        return Objects.equals(b.getInvoiceType(), POS_INVOICE_TYPE);
    }

    private boolean isOnDate(LocalDateTime dateTime, LocalDate date) {
        return dateTime != null && dateTime.toLocalDate().equals(date);
    }

    private String statusText(Integer status) {
        OrderStatus os = OrderStatus.of(status);
        return os != null ? os.getLabel() : "Không xác định";
    }

    private String csv(Object value) {
        String s = value == null ? "" : value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
