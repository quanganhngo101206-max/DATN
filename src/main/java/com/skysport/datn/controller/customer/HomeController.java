package com.skysport.datn.controller.customer;

import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.Image;
import com.skysport.datn.entity.Product;
import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private final ProductRepository productRepository;
    private final ProductDetailRepository productDetailRepository;
    private final CategoryRepository categoryRepository;
    private final ImageRepository imageRepository;

    // Constructor Injection (thay vì Field Injection)
    public HomeController(
            ProductRepository productRepository,
            ProductDetailRepository productDetailRepository,
            CategoryRepository categoryRepository,
            ImageRepository imageRepository) {
        this.productRepository = productRepository;
        this.productDetailRepository = productDetailRepository;
        this.categoryRepository = categoryRepository;
        this.imageRepository = imageRepository;
    }

    @GetMapping("/home")
    public String home(HttpSession session, Model model) {
        Account account = (Account) session.getAttribute("account");
        model.addAttribute("account", account);
        model.addAttribute("categories", categoryRepository.findByDeleteFlag(false));

        // Lấy tất cả sản phẩm
        List<Product> allProducts = productRepository.findByDeleteFlagFalse()
                .stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .collect(Collectors.toList());

        // Lấy giá từ ProductDetail (giá thấp nhất của mỗi sản phẩm)
        Map<Integer, Double> productPrices = new HashMap<>();
        Map<Integer, String> productImages = new HashMap<>();

        for (Product p : allProducts) {
            // Lấy giá thấp nhất từ các biến thể - SỬA LỖI Ở ĐÂY
            List<ProductDetail> details = productDetailRepository.findByProductIdAndDeleteFlagFalse(p.getId());
            Double minPrice = details.stream()
                    .filter(d -> d.getPrice() != null)
                    .mapToDouble(ProductDetail::getPrice)  // ✅ Dùng mapToDouble
                    .min()  // ✅ Trả về OptionalDouble
                    .orElse(0.0);  // ✅ Lấy giá trị hoặc 0
            productPrices.put(p.getId(), minPrice);

            // Lấy ảnh
            List<Image> imgs = imageRepository.findByProductId(p.getId());
            if (!imgs.isEmpty()) {
                productImages.put(p.getId(), imgs.get(0).getLink());
            }
        }

        // Sản phẩm nổi bật (4 sản phẩm đầu)
        List<Product> featured = allProducts.stream().limit(4).collect(Collectors.toList());

        // Sản phẩm mới nhất (sắp xếp theo ngày tạo)
        List<Product> newProducts = allProducts.stream()
                .sorted((a, b) -> {
                    if (a.getCreateDate() == null) return 1;
                    if (b.getCreateDate() == null) return -1;
                    return b.getCreateDate().compareTo(a.getCreateDate());
                })
                .limit(4)
                .collect(Collectors.toList());

        model.addAttribute("featuredProducts", featured);
        model.addAttribute("newProducts", newProducts);
        model.addAttribute("productPrices", productPrices);
        model.addAttribute("productImages", productImages);

        return "home";
    }
}