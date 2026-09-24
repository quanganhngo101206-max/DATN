package com.skysport.datn.controller.admin;

import com.skysport.datn.entity.*;
import com.skysport.datn.repository.ImageRepository;
import com.skysport.datn.service.ProductDetailService;
import com.skysport.datn.service.ProductDiscountService;
import com.skysport.datn.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductDetailService productDetailService;
    private final ImageRepository imageRepository;
    private final ProductDiscountService productDiscountService;

    // Whitelist đuôi file cho ảnh sản phẩm — chặn upload .html/.svg/.js núp dưới đuôi ảnh
    private static final Set<String> ALLOWED_IMAGE_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB

    // Danh sách sản phẩm (tìm kiếm + lọc + phân trang)
    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Integer categoryId,
                       @RequestParam(required = false) Integer brandId,
                       @RequestParam(required = false) Integer materialId,
                       @RequestParam(required = false) Integer sizeId,
                       @RequestParam(required = false) Integer colorId,
                       @RequestParam(required = false) Integer status,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       Model model) {
        populateListAttributes(model, keyword, categoryId, brandId, materialId, sizeId, colorId, status, page, size);
        model.addAttribute("product", new Product());
        return "admin/product/list";
    }

    // Gom logic tìm kiếm/lọc/phân trang dùng chung cho list(), addForm(), edit()
    private void populateListAttributes(Model model, String keyword, Integer categoryId, Integer brandId,
                                        Integer materialId, Integer sizeId, Integer colorId,
                                        Integer status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<Product> productPage = productService.search(keyword, categoryId, brandId, materialId,
                sizeId, colorId, status, pageable);

        List<Integer> productIds = productPage.getContent().stream().map(Product::getId).toList();

        model.addAttribute("productPage", productPage);
        model.addAttribute("products", productPage.getContent());
        model.addAttribute("quantities", productService.getQuantityMap(productIds));
        model.addAttribute("categories", productService.findAllCategory());
        model.addAttribute("brands", productService.findAllBrand());
        model.addAttribute("materials", productService.findAllMaterial());
        model.addAttribute("sizes", productDetailService.findAllActiveSize());
        model.addAttribute("colors", productDetailService.findAllActiveColor());
        // Danh sách chỉ gồm mục đang Hoạt động — dùng cho dropdown khi thêm/sửa sản phẩm
        model.addAttribute("activeCategories", productService.findAllActiveCategory());
        model.addAttribute("activeBrands", productService.findAllActiveBrand());
        model.addAttribute("activeMaterials", productService.findAllActiveMaterial());

        // Giữ lại giá trị tìm kiếm/lọc trên form để hiển thị lại sau khi submit
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedBrandId", brandId);
        model.addAttribute("selectedMaterialId", materialId);
        model.addAttribute("selectedSizeId", sizeId);
        model.addAttribute("selectedColorId", colorId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("pageSize", size);
    }

    // Thêm sản phẩm
    @PostMapping("/save")
    public String save(@ModelAttribute Product product,
                       @RequestParam Integer categoryId,
                       @RequestParam Integer brandId,
                       @RequestParam Integer materialId) {
        product.setCategory(productService.findAllActiveCategory()
                .stream().filter(c -> c.getId().equals(categoryId)).findFirst().orElse(null));
        product.setBrand(productService.findAllActiveBrand()
                .stream().filter(b -> b.getId().equals(brandId)).findFirst().orElse(null));
        product.setMaterial(productService.findAllActiveMaterial()
                .stream().filter(m -> m.getId().equals(materialId)).findFirst().orElse(null));
        productService.save(product);
        return "redirect:/admin/product";
    }

    @GetMapping("/add")
    public String addForm(Model model) {
        // Object rỗng cho form — modal thêm sản phẩm sẽ tự mở khi vào trang này
        model.addAttribute("product", new Product());
        model.addAttribute("openModal", true);
        populateListAttributes(model, null, null, null, null, null, null, null, 0, 10);
        return "admin/product/list";
    }

    // Chi tiết sản phẩm + quản lý product detail
    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        model.addAttribute("product", productService.findById(id));
        List<ProductDetail> details = productDetailService.findByProduct(id);
        model.addAttribute("details", details);
        // Tổng số lượng tồn kho = tổng quantity của các biến thể đang hoạt động (không tính biến thể đã xóa mềm)
        int totalQuantity = details.stream()
                .filter(d -> d.getDeleteFlag() == null || !d.getDeleteFlag())
                .mapToInt(d -> d.getQuantity() != null ? d.getQuantity() : 0)
                .sum();
        model.addAttribute("totalQuantity", totalQuantity);
        model.addAttribute("sizes", productDetailService.findAllActiveSize());
        model.addAttribute("colors", productDetailService.findAllActiveColor());

        model.addAttribute("allSizes", productDetailService.findAllSize());
        model.addAttribute("allColors", productDetailService.findAllColor());
        model.addAttribute("newDetail", new ProductDetail());
        model.addAttribute("images", imageRepository.findByProductId(id));
        return "admin/product/detail";
    }

    // Thêm ảnh sản phẩm (upload file)
    @PostMapping("/{productId}/image/add")
    public String addImage(@PathVariable Integer productId,
                           @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                           @RequestParam(required = false) String name,
                           RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("errorMsg", "Vui lòng chọn ảnh!");
            return "redirect:/admin/product/detail/" + productId;
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            ra.addFlashAttribute("errorMsg", "Ảnh vượt quá dung lượng cho phép (tối đa 5MB)!");
            return "redirect:/admin/product/detail/" + productId;
        }
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        }
        if (!ALLOWED_IMAGE_EXT.contains(ext)) {
            ra.addFlashAttribute("errorMsg", "Chỉ chấp nhận ảnh định dạng: jpg, jpeg, png, webp, gif!");
            return "redirect:/admin/product/detail/" + productId;
        }
        Product product = productService.findById(productId);
        if (product == null) {
            ra.addFlashAttribute("errorMsg", "Sản phẩm không tồn tại!");
            return "redirect:/admin/product";
        }

        try {
            // Xác thực nội dung file thực sự là ảnh giải mã được (chặn file đổi đuôi giả mạo,
            // vd. đặt tên evil.html.jpg hoặc đổi đuôi .svg/.js thành .jpg)
            byte[] fileBytes = file.getBytes();
            java.awt.image.BufferedImage decoded;
            try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(fileBytes)) {
                decoded = javax.imageio.ImageIO.read(bis);
            }
            if (decoded == null) {
                ra.addFlashAttribute("errorMsg", "File không phải là ảnh hợp lệ!");
                return "redirect:/admin/product/detail/" + productId;
            }

            java.nio.file.Path uploadPath = java.nio.file.Paths.get("uploads");
            if (!java.nio.file.Files.exists(uploadPath)) {
                java.nio.file.Files.createDirectories(uploadPath);
            }

            String newFilename = java.util.UUID.randomUUID().toString() + "." + ext;

            java.nio.file.Path filePath = uploadPath.resolve(newFilename);
            java.nio.file.Files.write(filePath, fileBytes);

            String linkUrl = "/uploads/" + newFilename;

            Image image = Image.builder()
                    .createDate(LocalDateTime.now())
                    .updateDate(LocalDateTime.now())
                    .fileType(ext.isEmpty() ? "jpg" : ext)
                    .link(linkUrl)
                    .name(name != null && !name.isBlank() ? name.trim() : product.getName())
                    .product(product)
                    .build();
            imageRepository.save(image);

            ra.addFlashAttribute("successMsg", "Đã thêm ảnh sản phẩm.");
        } catch (java.io.IOException e) {
            ra.addFlashAttribute("errorMsg", "Lỗi khi lưu ảnh: " + e.getMessage());
        }

        return "redirect:/admin/product/detail/" + productId;
    }

    // Xóa ảnh sản phẩm
    @PostMapping("/{productId}/image/delete/{imageId}")
    public String deleteImage(@PathVariable Integer productId,
                              @PathVariable Integer imageId,
                              RedirectAttributes ra) {
        Image image = imageRepository.findById(imageId).orElse(null);
        if (image == null || image.getProduct() == null
                || !image.getProduct().getId().equals(productId)) {
            ra.addFlashAttribute("errorMsg", "Ảnh không tồn tại!");
            return "redirect:/admin/product/detail/" + productId;
        }
        imageRepository.delete(image);
        ra.addFlashAttribute("successMsg", "Đã xóa ảnh sản phẩm.");
        return "redirect:/admin/product/detail/" + productId;
    }

    // Thêm product detail
    @PostMapping("/detail/save")
    public String saveDetail(@ModelAttribute ProductDetail detail,
                             @RequestParam Integer productId,
                             @RequestParam Integer sizeId,
                             @RequestParam Integer colorId,
                             RedirectAttributes ra) {
        Product product = productService.findById(productId);
        detail.setProduct(product);
        detail.setSize(productDetailService.findAllActiveSize()
                .stream().filter(s -> s.getId().equals(sizeId)).findFirst().orElse(null));
        detail.setColor(productDetailService.findAllActiveColor()
                .stream().filter(c -> c.getId().equals(colorId)).findFirst().orElse(null));
        try {
            productDetailService.save(detail);
            ra.addFlashAttribute("successMsg", "Đã thêm biến thể sản phẩm.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/product/detail/" + productId;
    }

    // Cập nhật product detail
    @PostMapping("/detail/update/{id}")
    public String updateDetail(@PathVariable Integer id,
                               @ModelAttribute ProductDetail updatedDetail,
                               @RequestParam Integer productId,
                               @RequestParam Integer sizeId,
                               @RequestParam Integer colorId,
                               RedirectAttributes ra) {
        ProductDetail oldDetail = productDetailService.findById(id);
        if (oldDetail == null) {
            ra.addFlashAttribute("errorMsg", "Biến thể không tồn tại!");
            return "redirect:/admin/product/detail/" + productId;
        }

        Size selectedSize = productDetailService.findAllActiveSize()
                .stream()
                .filter(s -> s.getId().equals(sizeId))
                .findFirst()
                .orElse(null);

        Color selectedColor = productDetailService.findAllActiveColor()
                .stream()
                .filter(c -> c.getId().equals(colorId))
                .findFirst()
                .orElse(null);

        if (selectedSize == null) {
            ra.addFlashAttribute("errorMsg", "Size đã bị tạm dừng, không thể chọn Size này!");
            return "redirect:/admin/product/detail/" + productId;
        }

        if (selectedColor == null) {
            ra.addFlashAttribute("errorMsg", "Màu đã bị tạm dừng, không thể chọn Màu này!");
            return "redirect:/admin/product/detail/" + productId;
        }

        oldDetail.setSize(selectedSize);
        oldDetail.setColor(selectedColor);
        oldDetail.setQuantity(updatedDetail.getQuantity());
        oldDetail.setPrice(updatedDetail.getPrice());
        oldDetail.setBarcode(updatedDetail.getBarcode());
        oldDetail.setStatus(updatedDetail.getStatus());
        oldDetail.setMinStock(updatedDetail.getMinStock());
        oldDetail.setMaxStock(updatedDetail.getMaxStock());
        oldDetail.setTargetMarginPercent(updatedDetail.getTargetMarginPercent());

        try {
            productDetailService.update(oldDetail);
            ra.addFlashAttribute("successMsg", "Đã cập nhật biến thể sản phẩm.");
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            ra.addFlashAttribute("errorMsg", "Không thể cập nhật: biến thể với Size và Màu này đã tồn tại hoặc trùng Barcode.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/product/detail/" + productId;
    }

    // Form sửa sản phẩm — modal sửa sẽ tự mở với dữ liệu đã điền sẵn
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Integer id, Model model) {
        model.addAttribute("product", productService.findById(id));
        model.addAttribute("openModal", true);
        populateListAttributes(model, null, null, null, null, null, null, null, 0, 10);
        return "admin/product/list";
    }

    // Cập nhật sản phẩm
    @PostMapping("/update")
    public String update(@ModelAttribute Product product,
                         @RequestParam Integer categoryId,
                         @RequestParam Integer brandId,
                         @RequestParam Integer materialId) {
        Product old = productService.findById(product.getId());
        old.setCode(product.getCode());
        old.setName(product.getName());
        old.setGender(product.getGender());
        old.setDescribe(product.getDescribe());
        old.setCategory(productService.findAllActiveCategory()
                .stream().filter(c -> c.getId().equals(categoryId)).findFirst().orElse(old.getCategory()));
        old.setBrand(productService.findAllActiveBrand()
                .stream().filter(b -> b.getId().equals(brandId)).findFirst().orElse(old.getBrand()));
        old.setMaterial(productService.findAllActiveMaterial()
                .stream().filter(m -> m.getId().equals(materialId)).findFirst().orElse(old.getMaterial()));
        productService.update(old);
        return "redirect:/admin/product";
    }

    // Bật / tắt trạng thái sản phẩm
    @PostMapping("/toggle-status/{id}")
    public String toggleStatus(@PathVariable Integer id) {
        productService.toggleStatus(id);
        return "redirect:/admin/product";
    }

    // Áp khuyến mãi cho 1 biến thể sản phẩm
    @PostMapping("/detail/{detailId}/discount/save")
    public String saveDiscount(@PathVariable Integer detailId,
                               @RequestParam Float discountedAmount,
                               @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startDate,
                               @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endDate,
                               RedirectAttributes ra) {
        ProductDetail detail = productDetailService.findById(detailId);
        if (detail == null || detail.getProduct() == null) {
            ra.addFlashAttribute("errorMsg", "Biến thể không tồn tại!");
            return "redirect:/admin/product";
        }
        try {
            productDiscountService.createDiscount(detailId, discountedAmount, startDate, endDate);
            ra.addFlashAttribute("successMsg", "Đã áp dụng khuyến mãi cho biến thể.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/product/detail/" + detail.getProduct().getId();
    }

    // Tắt khuyến mãi trước hạn
    @PostMapping("/detail/discount/close/{id}")
    public String closeDiscount(@PathVariable Integer id, @RequestParam Integer productId, RedirectAttributes ra) {
        try {
            productDiscountService.closeDiscount(id);
            ra.addFlashAttribute("successMsg", "Đã tắt khuyến mãi.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/product/detail/" + productId;
    }
}