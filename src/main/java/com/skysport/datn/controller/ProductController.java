package com.skysport.datn.controller;

import com.skysport.datn.entity.Product;
import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.service.ProductDetailService;
import com.skysport.datn.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

//@Controller
@RequestMapping("/admin/product")
public class ProductController {

    @Autowired
    private ProductService productService;
    @Autowired
    private ProductDetailService productDetailService;

    // Danh sÃƒÂ¡ch sÃ¡ÂºÂ£n phÃ¡ÂºÂ©m
    @GetMapping
    public String list(Model model) {
        model.addAttribute("products", productService.findAll());
        model.addAttribute("product", new Product());
        model.addAttribute("categories", productService.findAllCategory());
        model.addAttribute("brands", productService.findAllBrand());
        model.addAttribute("materials", productService.findAllMaterial());
        return "admin/product/list";
    }

    // ThÃƒÂªm sÃ¡ÂºÂ£n phÃ¡ÂºÂ©m
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

    // Chi tiÃ¡ÂºÂ¿t sÃ¡ÂºÂ£n phÃ¡ÂºÂ©m + quÃ¡ÂºÂ£n lÃƒÂ½ product detail
    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        model.addAttribute("product", productService.findById(id));
        model.addAttribute("details", productDetailService.findByProduct(id));
        model.addAttribute("sizes", productDetailService.findAllSize());
        model.addAttribute("colors", productDetailService.findAllColor());
        model.addAttribute("newDetail", new ProductDetail());
        return "admin/product/detail";
    }

    // ThÃƒÂªm product detail
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

    // Form sÃ¡Â»Â­a sÃ¡ÂºÂ£n phÃ¡ÂºÂ©m
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Integer id, Model model) {
        model.addAttribute("product", productService.findById(id));
        model.addAttribute("products", productService.findAll());
        model.addAttribute("categories", productService.findAllCategory());
        model.addAttribute("brands", productService.findAllBrand());
        model.addAttribute("materials", productService.findAllMaterial());
        return "admin/product/list";
    }

    // CÃ¡ÂºÂ­p nhÃ¡ÂºÂ­t sÃ¡ÂºÂ£n phÃ¡ÂºÂ©m
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
        old.setStatus(product.getStatus()); // Ã¢Å“â€¦
        old.setCategory(productService.findAllCategory()
                .stream().filter(c -> c.getId().equals(categoryId)).findFirst().orElse(null));
        old.setBrand(productService.findAllBrand()
                .stream().filter(b -> b.getId().equals(brandId)).findFirst().orElse(null));
        old.setMaterial(productService.findAllMaterial()
                .stream().filter(m -> m.getId().equals(materialId)).findFirst().orElse(null));
        productService.update(old);
        return "redirect:/admin/product";
    }

    // XÃƒÂ³a sÃ¡ÂºÂ£n phÃ¡ÂºÂ©m
    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Integer id) {
        productService.delete(id);
        return "redirect:/admin/product";
    }

    @GetMapping("/detail/delete/{id}")
    public String deleteDetail(@PathVariable Integer id) {
        ProductDetail detail = productDetailService.findById(id);
        Integer productId = detail.getProduct().getId();
        productDetailService.delete(id);
        return "redirect:/admin/product/detail/" + productId;
    }
}
