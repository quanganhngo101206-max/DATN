package com.skysport.datn.controller.admin;

import com.skysport.datn.entity.*;
import com.skysport.datn.enums.ImportOrderStatus;
import com.skysport.datn.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/import")
public class AdminImportController {

    @Autowired private ImportOrderRepository importOrderRepository;
    @Autowired private ImportOrderDetailRepository importOrderDetailRepository;
    @Autowired private ProductDetailRepository productDetailRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private StaffRepository staffRepository;

    @GetMapping
    public String list(@RequestParam(required = false) Integer status, Model model) {
        List<ImportOrder> orders = (status != null)
                ? importOrderRepository.findByStatus(status)
                : importOrderRepository.findAllByOrderByCreateDateDesc();
        if (orders == null) orders = List.of();

        Map<Integer, Double> totalMap = new HashMap<>();
        for (ImportOrder o : orders) {
            Double t = importOrderDetailRepository.sumTotalByOrderId(o.getId());
            totalMap.put(o.getId(), t != null ? t : 0.0);
        }

        model.addAttribute("orders", orders);
        model.addAttribute("totalMap", totalMap);
        model.addAttribute("currentStatus", status != null ? status : -1);
        model.addAttribute("pendingCount", importOrderRepository.findByStatus(1).size());
        return "admin/import/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("suppliers", supplierRepository.findByStatusAndDeleteFlagFalse(1));
        model.addAttribute("productDetails", productDetailRepository.findAll()
                .stream().filter(pd -> pd.getDeleteFlag() == null || !pd.getDeleteFlag()).toList());
        return "admin/import/create";
    }

    // Admin tạo → tự động duyệt (status=2), cộng tồn kho ngay
    @PostMapping("/save")
    @Transactional
    public String save(@RequestParam Integer supplierId,
                       @RequestParam String note,
                       @RequestParam List<Integer> productDetailIds,
                       @RequestParam List<Integer> quantities,
                       @RequestParam List<Double> importPrices,
                       RedirectAttributes ra) {
        try {
            if (productDetailIds == null || productDetailIds.isEmpty())
                throw new RuntimeException("Vui lòng chọn ít nhất một sản phẩm!");
            if (productDetailIds.size() != quantities.size()
                    || productDetailIds.size() != importPrices.size())
                throw new RuntimeException("Dữ liệu sản phẩm không hợp lệ!");

            double total = 0;
            for (int i = 0; i < quantities.size(); i++)
                total += quantities.get(i) * importPrices.get(i);

            Supplier supplier = supplierRepository.findById(supplierId).orElse(null);

            ImportOrder order = ImportOrder.builder()
                    .code("PN" + System.currentTimeMillis())
                    .createDate(LocalDateTime.now())
                    .updateDate(LocalDateTime.now())
                    .totalAmount(total)
                    .status(2)        // Admin tạo → duyệt ngay
                    .note(note)
                    .staff(null)
                    .supplier(supplier)
                    .build();
            importOrderRepository.save(order);

            for (int i = 0; i < productDetailIds.size(); i++) {
                ProductDetail pd = productDetailRepository
                        .findById(productDetailIds.get(i)).orElse(null);
                if (pd == null) continue;

                importOrderDetailRepository.save(ImportOrderDetail.builder()
                        .importOrder(order)
                        .productDetail(pd)
                        .quantity(quantities.get(i))
                        .importPrice(importPrices.get(i).floatValue())
                        .build());

                pd.setQuantity((pd.getQuantity() != null ? pd.getQuantity() : 0)
                        + quantities.get(i));
                productDetailRepository.save(pd);
            }

            ra.addFlashAttribute("successMsg",
                    "Tạo phiếu nhập " + order.getCode() + " thành công! Tồn kho đã được cập nhật.");
            return "redirect:/admin/import";

        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Lỗi: " + e.getMessage());
            return "redirect:/admin/import/create";
        }
    }

    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        ImportOrder order = importOrderRepository.findById(id).orElse(null);
        if (order == null) return "redirect:/admin/import";

        List<ImportOrderDetail> details = importOrderDetailRepository.findByImportOrderId(id);

        double calculatedTotal = 0;
        for (ImportOrderDetail d : details)
            calculatedTotal += d.getImportPrice() * d.getQuantity();

        model.addAttribute("order", order);
        model.addAttribute("details", details);
        model.addAttribute("calculatedTotal", calculatedTotal);
        return "admin/import/detail";
    }

    // Duyệt phiếu nhập → status=2, cộng tồn kho
    @PostMapping("/approve/{id}")
    @Transactional
    public String approve(@PathVariable Integer id,
                          @RequestParam(required = false, defaultValue = "/admin/import") String redirect,
                          RedirectAttributes ra) {
        ImportOrder order = importOrderRepository.findById(id).orElse(null);
        if (order == null || !ImportOrderStatus.PENDING.matches(order.getStatus())) {
            ra.addFlashAttribute("errorMsg", "Phiếu nhập không hợp lệ hoặc đã xử lý!");
            return "redirect:" + redirect;
        }

        List<ImportOrderDetail> details = importOrderDetailRepository.findByImportOrderId(id);
        for (ImportOrderDetail d : details) {
            if (d.getProductDetail() == null || d.getQuantity() == null) continue;
            ProductDetail pd = productDetailRepository
                    .findById(d.getProductDetail().getId()).orElse(null);
            if (pd != null) {
                pd.setQuantity((pd.getQuantity() != null ? pd.getQuantity() : 0) + d.getQuantity());
                productDetailRepository.save(pd);
            }
        }

        Double realTotal = importOrderDetailRepository.sumTotalByOrderId(id);
        order.setTotalAmount(realTotal != null ? realTotal : 0.0);
        order.setStatus(ImportOrderStatus.APPROVED.getValue());
        order.setUpdateDate(LocalDateTime.now());
        importOrderRepository.save(order);

        ra.addFlashAttribute("successMsg",
                "Đã duyệt phiếu nhập " + order.getCode() + ". Tồn kho đã được cập nhật.");
        return "redirect:" + redirect;
    }

    // Từ chối phiếu nhập → status=3
    @PostMapping("/reject/{id}")
    public String reject(@PathVariable Integer id,
                         @RequestParam(required = false) String note,
                         @RequestParam(required = false, defaultValue = "/admin/import") String redirect,
                         RedirectAttributes ra) {
        ImportOrder order = importOrderRepository.findById(id).orElse(null);
        if (order != null && ImportOrderStatus.PENDING.matches(order.getStatus())) {
            order.setStatus(ImportOrderStatus.REJECTED.getValue());
            order.setUpdateDate(LocalDateTime.now());
            if (note != null && !note.isBlank()) order.setNote(note);
            importOrderRepository.save(order);
            ra.addFlashAttribute("successMsg", "Đã từ chối phiếu nhập " + order.getCode() + ".");
        } else {
            ra.addFlashAttribute("errorMsg", "Không thể từ chối phiếu này.");
        }
        return "redirect:" + redirect;
    }
}