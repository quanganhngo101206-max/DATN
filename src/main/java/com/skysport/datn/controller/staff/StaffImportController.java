package com.skysport.datn.controller.staff;

import com.skysport.datn.entity.*;
import com.skysport.datn.repository.*;
import jakarta.servlet.http.HttpSession;
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
@RequestMapping("/staff/import")
public class StaffImportController {

    @Autowired private ImportOrderRepository importOrderRepository;
    @Autowired private ImportOrderDetailRepository importOrderDetailRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private ProductDetailRepository productDetailRepository;
    @Autowired private StaffRepository staffRepository;

    @GetMapping
    public String list(HttpSession session, Model model) {
        Account account = (Account) session.getAttribute("account");
        Staff staff = staffRepository.findByAccountId(account.getId());
        model.addAttribute("staff", staff);

        List<ImportOrder> orders = importOrderRepository.findAllByOrderByCreateDateDesc();
        if (orders == null) orders = List.of();

        Map<Integer, Double> totalMap = new HashMap<>();
        for (ImportOrder o : orders) {
            Double t = importOrderDetailRepository.sumTotalByOrderId(o.getId());
            totalMap.put(o.getId(), t != null ? t : 0.0);
        }

        model.addAttribute("orders", orders);
        model.addAttribute("totalMap", totalMap);
        return "staff/import/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("suppliers", supplierRepository.findByStatusAndDeleteFlagFalse(1));
        model.addAttribute("productDetails", productDetailRepository.findAll()
                .stream().filter(pd -> pd.getDeleteFlag() == null || !pd.getDeleteFlag()).toList());
        return "staff/import/create";
    }

    // Staff tạo → status=1 (chờ Admin duyệt)
    @PostMapping("/save")
    @Transactional
    public String save(@RequestParam Integer supplierId,
                       @RequestParam String note,
                       @RequestParam List<Integer> productDetailIds,
                       @RequestParam List<Integer> quantities,
                       @RequestParam List<Double> importPrices,
                       HttpSession session,
                       RedirectAttributes ra) {
        try {
            if (productDetailIds == null || productDetailIds.isEmpty())
                throw new RuntimeException("Vui lòng chọn ít nhất một sản phẩm!");
            if (productDetailIds.size() != quantities.size()
                    || productDetailIds.size() != importPrices.size())
                throw new RuntimeException("Dữ liệu sản phẩm không hợp lệ!");

            Account account = (Account) session.getAttribute("account");
            Staff staff = staffRepository.findByAccountId(account.getId());

            double total = 0;
            for (int i = 0; i < quantities.size(); i++)
                total += quantities.get(i) * importPrices.get(i);

            Supplier supplier = supplierRepository.findById(supplierId).orElse(null);

            ImportOrder order = ImportOrder.builder()
                    .code("PN" + System.currentTimeMillis())
                    .createDate(LocalDateTime.now())
                    .updateDate(LocalDateTime.now())
                    .totalAmount(total)
                    .status(1)        // Staff tạo → chờ Admin duyệt
                    .note(note)
                    .staff(staff)
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
                // Không cộng tồn kho — chờ Admin duyệt mới cộng
            }

            ra.addFlashAttribute("successMsg",
                    "Tạo phiếu nhập " + order.getCode() + " thành công! Đang chờ Admin duyệt.");
            return "redirect:/staff/import";

        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Lỗi: " + e.getMessage());
            return "redirect:/staff/import/create";
        }
    }

    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Integer id, Model model, HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        if (account != null) {
            Staff staff = staffRepository.findByAccountId(account.getId());
            model.addAttribute("staff", staff);
        }

        ImportOrder order = importOrderRepository.findById(id).orElse(null);
        if (order == null) return "redirect:/staff/import";

        List<ImportOrderDetail> details = importOrderDetailRepository.findByImportOrderId(id);

        double calculatedTotal = 0;
        for (ImportOrderDetail d : details)
            calculatedTotal += d.getImportPrice() * d.getQuantity();

        model.addAttribute("order", order);
        model.addAttribute("details", details);
        model.addAttribute("calculatedTotal", calculatedTotal);
        return "staff/import/detail";
    }
}