package com.skysport.datn.controller.admin;

import com.skysport.datn.entity.*;
import com.skysport.datn.enums.ImportOrderStatus;
import com.skysport.datn.repository.*;
import com.skysport.datn.service.ImportPriceService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import com.skysport.datn.exception.BusinessException;

@Controller
@RequestMapping("/admin/import")
@RequiredArgsConstructor
public class AdminImportController {

    private final ImportOrderRepository importOrderRepository;
    private final ImportOrderDetailRepository importOrderDetailRepository;
    private final ProductDetailRepository productDetailRepository;
    private final SupplierRepository supplierRepository;
    private final StaffRepository staffRepository;
    private final ImportPriceService importPriceService;

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

        List<ProductDetail> productDetails = productDetailRepository.findAll()
                .stream()
                .filter(pd -> pd.getDeleteFlag() == null || !pd.getDeleteFlag())
                .toList();

        Map<Integer, Float> lastImportPriceMap = new HashMap<>();

        for (ProductDetail pd : productDetails) {
            List<Float> prices = importOrderDetailRepository
                    .findLatestImportPricesByProductDetailId(pd.getId());

            if (prices != null && !prices.isEmpty()) {
                lastImportPriceMap.put(pd.getId(), prices.get(0));
            }
        }

        model.addAttribute("productDetails", productDetails);
        model.addAttribute("lastImportPriceMap", lastImportPriceMap);

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
                throw new BusinessException("Vui lòng chọn ít nhất một sản phẩm!");

            if (productDetailIds.size() != quantities.size()
                    || productDetailIds.size() != importPrices.size())
                throw new BusinessException("Dữ liệu sản phẩm không hợp lệ!");

            Supplier supplier = supplierRepository.findById(supplierId).orElse(null);
            if (supplier == null) {
                throw new BusinessException("Nhà cung cấp không tồn tại!");
            }

            Set<Integer> productDetailIdSet = new HashSet<>();
            StringBuilder warningMessage = new StringBuilder();

            for (int i = 0; i < productDetailIds.size(); i++) {
                Integer productDetailId = productDetailIds.get(i);
                Integer quantity = quantities.get(i);
                Double importPrice = importPrices.get(i);

                if (productDetailId == null) {
                    throw new BusinessException("Sản phẩm không hợp lệ!");
                }

                if (!productDetailIdSet.add(productDetailId)) {
                    throw new BusinessException("Không được nhập trùng cùng một biến thể sản phẩm trong một phiếu!");
                }

                if (quantity == null || quantity <= 0) {
                    throw new BusinessException("Số lượng nhập phải lớn hơn 0!");
                }

                if (importPrice == null || importPrice < 0) {
                    throw new BusinessException("Giá nhập không hợp lệ!");
                }

                ProductDetail pd = productDetailRepository.findById(productDetailId).orElse(null);

                if (pd == null
                        || Boolean.TRUE.equals(pd.getDeleteFlag())
                        || pd.getProduct() == null) {
                    throw new BusinessException("Biến thể sản phẩm không tồn tại hoặc đã bị xóa!");
                }

                int currentStock = pd.getQuantity() != null ? pd.getQuantity() : 0;
                int maxStock = pd.getMaxStock() != null ? pd.getMaxStock() : 50;
                int stockAfterImport = currentStock + quantity;

                if (stockAfterImport > maxStock) {
                    warningMessage.append("Sản phẩm \"")
                            .append(pd.getProduct().getName())
                            .append("\" sẽ vượt tồn tối đa: ")
                            .append(stockAfterImport)
                            .append("/")
                            .append(maxStock)
                            .append(". ");
                }

                Float importPriceFloat = importPrice.floatValue();

                if (importPriceService.isImportPriceHigherThanSellingPrice(pd, importPriceFloat)) {
                    warningMessage.append("Giá nhập sản phẩm \"")
                            .append(pd.getProduct().getName())
                            .append("\" cao hơn giá bán hiện tại. ");
                }

                Float suggestedPrice = importPriceService
                        .calculateSuggestedSellingPrice(pd, importPriceFloat);

                if (suggestedPrice != null
                        && importPriceService.isSuggestedPriceHigherThanCurrentPrice(pd, importPriceFloat)) {
                    warningMessage.append("Giá bán đề xuất của sản phẩm \"")
                            .append(pd.getProduct().getName())
                            .append("\" cao hơn giá bán hiện tại. ");
                }
            }

            double total = 0;
            for (int i = 0; i < quantities.size(); i++)
                total += quantities.get(i) * importPrices.get(i);

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

            if (warningMessage.length() > 0) {
                ra.addFlashAttribute("warningMsg",
                        "Tạo phiếu nhập " + order.getCode() + " thành công! "
                                + warningMessage);
            } else {
                ra.addFlashAttribute("successMsg",
                        "Tạo phiếu nhập " + order.getCode() + " thành công! Tồn kho đã được cập nhật.");
            }

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
        Map<Integer, Float> suggestedPriceMap = new HashMap<>();

        for (ImportOrderDetail d : details) {
            calculatedTotal += d.getImportPrice() * d.getQuantity();

            if (d.getProductDetail() != null) {
                Float suggestedPrice = importPriceService.calculateSuggestedSellingPrice(
                        d.getProductDetail(),
                        d.getImportPrice()
                );

                suggestedPriceMap.put(d.getId(), suggestedPrice);
            }
        }

        model.addAttribute("order", order);
        model.addAttribute("details", details);
        model.addAttribute("calculatedTotal", calculatedTotal);
        model.addAttribute("suggestedPriceMap", suggestedPriceMap);
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

            if (note != null && !note.isBlank())
                order.setNote(note);

            importOrderRepository.save(order);

            ra.addFlashAttribute("successMsg",
                    "Đã từ chối phiếu nhập " + order.getCode() + ".");
        } else {
            ra.addFlashAttribute("errorMsg", "Không thể từ chối phiếu này.");
        }

        return "redirect:" + redirect;
    }

    // Cập nhật giá bán sau khi phiếu nhập đã được duyệt
    @PostMapping("/update-selling-price/{detailId}")
    @Transactional
    public String updateSellingPrice(@PathVariable Integer detailId,
                                     @RequestParam Float sellingPrice,
                                     RedirectAttributes ra) {
        ImportOrderDetail importDetail = importOrderDetailRepository
                .findById(detailId)
                .orElse(null);

        if (importDetail == null || importDetail.getProductDetail() == null) {
            ra.addFlashAttribute("errorMsg", "Chi tiết phiếu nhập không tồn tại!");
            return "redirect:/admin/import";
        }

        ImportOrder order = importDetail.getImportOrder();

        if (order == null || !ImportOrderStatus.APPROVED.matches(order.getStatus())) {
            ra.addFlashAttribute("errorMsg",
                    "Chỉ được cập nhật giá bán sau khi phiếu nhập đã được duyệt!");
            return "redirect:/admin/import/detail/"
                    + (order != null ? order.getId() : "");
        }

        if (sellingPrice == null || sellingPrice <= 0) {
            ra.addFlashAttribute("errorMsg", "Giá bán phải lớn hơn 0!");
            return "redirect:/admin/import/detail/" + order.getId();
        }

        ProductDetail pd = importDetail.getProductDetail();
        Float importPrice = importDetail.getImportPrice();

        pd.setPrice(sellingPrice);
        productDetailRepository.save(pd);

        String message = "Đã cập nhật giá bán sản phẩm \""
                + pd.getProduct().getName()
                + "\" thành "
                + String.format("%,.0f", sellingPrice)
                + "đ.";

        if (importPrice != null && sellingPrice < importPrice) {
            message += " Cảnh báo: giá bán mới thấp hơn giá nhập "
                    + String.format("%,.0f", importPrice)
                    + "đ.";
            ra.addFlashAttribute("warningMsg", message);
        } else {
            ra.addFlashAttribute("successMsg", message);
        }

        return "redirect:/admin/import/detail/" + order.getId();
    }
}