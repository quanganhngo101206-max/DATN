package com.skysport.datn.controller.admin;

import com.skysport.datn.dto.response.SalesReportDto;
import com.skysport.datn.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

@Controller
@RequestMapping("/admin/report")
@RequiredArgsConstructor
public class AdminReportController {

    private final StatisticsService statisticsService;

    @GetMapping("/sales")
    public String salesReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false, name = "q") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        LocalDate[] range = resolveRange(from, to, preset);
        SalesReportDto report = statisticsService.getSalesReport(range[0], range[1]);
        Page<Map<String, Object>> orderPage = statisticsService.getSalesOrderPage(
                report.getFromDate(),
                report.getToDate(),
                status,
                channel,
                keyword,
                page,
                size);

        model.addAttribute("report", report);
        model.addAttribute("orderPage", orderPage);
        model.addAttribute("from", report.getFromDate());
        model.addAttribute("to", report.getToDate());
        model.addAttribute("preset", preset != null ? preset : "30d");
        model.addAttribute("statusFilter", status);
        model.addAttribute("channelFilter", channel);
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageSize", orderPage.getSize());
        model.addAttribute("startPage", Math.max(0, orderPage.getNumber() - 2));
        model.addAttribute("endPage", Math.min(
                Math.max(orderPage.getTotalPages() - 1, 0),
                orderPage.getNumber() + 2));
        model.addAttribute("activeMenu", "report");
        return "admin/report/sales";
    }

    @GetMapping("/sales/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String preset) {

        LocalDate[] range = resolveRange(from, to, preset);
        String csv = statisticsService.exportSalesCsv(range[0], range[1]);
        String filename = "bao-cao-doanh-thu_" + range[0] + "_" + range[1] + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/sales/export-excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false, defaultValue = "all") String type,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false, name = "q") String keyword) throws Exception {

        LocalDate[] range = resolveRange(from, to, preset);
        byte[] excel = statisticsService.exportSalesExcel(
                range[0], range[1], type, status, channel, keyword);

        String typeLabel = switch (type == null ? "all" : type.toLowerCase()) {
            case "products" -> "san-pham";
            case "categories" -> "danh-muc";
            case "customers" -> "khach-hang";
            case "orders" -> "don-hang";
            default -> "tong-hop";
        };
        String filename = "bao-cao-" + typeLabel + "_" + range[0] + "_" + range[1] + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    private LocalDate[] resolveRange(LocalDate from, LocalDate to, String preset) {
        LocalDate today = LocalDate.now();
        if (from != null || to != null) {
            return new LocalDate[]{
                    from != null ? from : today.minusDays(29),
                    to != null ? to : today
            };
        }
        if (preset == null) preset = "30d";
        return switch (preset) {
            case "today" -> new LocalDate[]{today, today};
            case "7d" -> new LocalDate[]{today.minusDays(6), today};
            case "month" -> new LocalDate[]{today.withDayOfMonth(1), today};
            default -> new LocalDate[]{today.minusDays(29), today};
        };
    }
}
