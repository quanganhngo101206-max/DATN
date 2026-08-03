package com.skysport.datn.controller.admin;

import com.skysport.datn.entity.Product;
import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.entity.Image;
import com.skysport.datn.repository.ImageRepository;
import com.skysport.datn.service.ProductDetailService;
import com.skysport.datn.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin/product")
public class ProductController {

    @Autowired
    private ProductService productService;
    @Autowired
    private ProductDetailService productDetailService;
    @Autowired
    private ImageRepository imageRepository;

    // Danh sách sản phẩm
    @GetMapping
    public String list(Model model) {
        model.addAttribute("products", productService.findAll());
        model.addAttribute("product", new Product());
        model.addAttribute("categories", productService.findAllCategory());
        model.addAttribute("brands", productService.findAllBrand());
        model.addAttribute("materials", productService.findAllMaterial());
        return "admin/product/list";
    }

    // Thêm sản phẩm
    @PostMapping("/save")
    public String save(@ModelAttribute Product product,
                       @RequestParam Integer categoryId,
                       @RequestParam Integer brandId,
                       @RequestParam Integer materialId) {
        product.setCategory(productService.findAllCategory()
                .stream().filter(c -> c.getId().equals(categoryId)).findFirst().orElse(null));
        product.setBrand(productService.findAllBrand()
                .stream().filter(b -> b.getId().equals(brandId)).findFirst().orElse(null));
        product.setMaterial(productService.findAllMaterial()
                .stream().filter(m -> m.getId().equals(materialId)).findFirst().orElse(null));
        productService.save(product);
        return "redirect:/admin/product";
    }

    @GetMapping("/add")
    public String addForm(Model model) {
        System.out.println("=== Đang gọi /admin/product/add ===");

        // ✅ Quan trọng: Phải có đủ các attribute này
        model.addAttribute("product", new Product());           // Object rỗng cho form
        model.addAttribute("products", productService.findAll());  // Cho bảng danh sách
        model.addAttribute("categories", productService.findAllCategory());
        model.addAttribute("brands", productService.findAllBrand());
        model.addAttribute("materials", productService.findAllMaterial());

        System.out.println("Categories: " + productService.findAllCategory().size());
        System.out.println("Brands: " + productService.findAllBrand().size());
        System.out.println("Materials: " + productService.findAllMaterial().size());

        return "admin/product/list";
    }

    // Chi tiết sản phẩm + quản lý product detail
    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        model.addAttribute("product", productService.findById(id));
        model.addAttribute("details", productDetailService.findByProduct(id));
        model.addAttribute("sizes", productDetailService.findAllSize());
        model.addAttribute("colors", productDetailService.findAllColor());
        model.addAttribute("newDetail", new ProductDetail());
        model.addAttribute("images", imageRepository.findByProductId(id));
        return "admin/product/detail";
    }

    // Thêm ảnh sản phẩm (theo link URL)
    @PostMapping("/{productId}/image/add")
    public String addImage(@PathVariable Integer productId,
                           @RequestParam String link,
                           @RequestParam(required = false) String name,
                           RedirectAttributes ra) {
        if (link == null || link.isBlank()) {
            ra.addFlashAttribute("errorMsg", "Vui lòng nhập link ảnh!");
            return "redirect:/admin/product/detail/" + productId;
        }
        Product product = productService.findById(productId);
        if (product == null) {
            ra.addFlashAttribute("errorMsg", "Sản phẩm không tồn tại!");
            return "redirect:/admin/product";
        }

        String link2 = link.trim();
        String ext = "";
        int dot = link2.lastIndexOf('.');
        if (dot >= 0 && dot < link2.length() - 1) {
            ext = link2.substring(dot + 1).split("[?#]")[0];
        }

        Image image = Image.builder()
                .createDate(LocalDateTime.now())
                .updateDate(LocalDateTime.now())
                .fileType(ext.isBlank() ? "jpg" : ext)
                .link(link2)
                .name(name != null && !name.isBlank() ? name.trim() : product.getName())
                .product(product)
                .build();
        imageRepository.save(image);

        ra.addFlashAttribute("successMsg", "Đã thêm ảnh sản phẩm.");
        return "redirect:/admin/product/detail/" + productId;
    }

    // Xóa ảnh sản phẩm
    @GetMapping("/{productId}/image/delete/{imageId}")
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
                             @RequestParam Integer colorId) {
        Product product = productService.findById(productId);
        detail.setProduct(product);
        detail.setSize(productDetailService.findAllSize()
                .stream().filter(s -> s.getId().equals(sizeId)).findFirst().orElse(null));
        detail.setColor(productDetailService.findAllColor()
                .stream().filter(c -> c.getId().equals(colorId)).findFirst().orElse(null));
        productDetailService.save(detail);
        return "redirect:/admin/product/detail/" + productId;
    }

    // Form sửa sản phẩm
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Integer id, Model model) {
        model.addAttribute("product", productService.findById(id));
        model.addAttribute("products", productService.findAll());
        model.addAttribute("categories", productService.findAllCategory());
        model.addAttribute("brands", productService.findAllBrand());
        model.addAttribute("materials", productService.findAllMaterial());
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
        old.setStatus(product.getStatus()); // ✅
        old.setCategory(productService.findAllCategory()
                .stream().filter(c -> c.getId().equals(categoryId)).findFirst().orElse(null));
        old.setBrand(productService.findAllBrand()
                .stream().filter(b -> b.getId().equals(brandId)).findFirst().orElse(null));
        old.setMaterial(productService.findAllMaterial()
                .stream().filter(m -> m.getId().equals(materialId)).findFirst().orElse(null));
        productService.update(old);
        return "redirect:/admin/product";
    }

    // Xóa sản phẩm
    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Integer id) {
        productService.delete(id);
        return "redirect:/admin/product";
    }

    @GetMapping("/detail/delete/{id}")
    public String deleteDetail(@PathVariable Integer id) {
        ProductDetail detail = productDetailService.findById(id);
        if (detail == null || detail.getProduct() == null) {
            return "redirect:/admin/product";
        }
        Integer productId = detail.getProduct().getId();
        productDetailService.delete(id);
        return "redirect:/admin/product/detail/" + productId;
    }
}