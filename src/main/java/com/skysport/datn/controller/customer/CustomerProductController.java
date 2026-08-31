package com.skysport.datn.controller.customer;

import com.skysport.datn.entity.Image;
import com.skysport.datn.entity.Product;
import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.entity.Review;
import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.Customer;
import com.skysport.datn.entity.Bill;
import com.skysport.datn.entity.BillDetail;
import com.skysport.datn.entity.Wishlist;
import com.skysport.datn.entity.WishlistDetail;
import com.skysport.datn.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class CustomerProductController {

    private final ProductRepository productRepository;
    private final ProductDetailRepository productDetailRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ImageRepository imageRepository;
    private final ReviewRepository reviewRepository;
    private final CustomerRepository customerRepository;
    private final BillRepository billRepository;
    private final BillDetailRepository billDetailRepository;
    private final WishlistRepository wishlistRepository;
    private final WishlistDetailRepository wishlistDetailRepository;

    // Sử dụng Constructor Injection thay vì Field Injection (tốt hơn)
    public CustomerProductController(
            ProductRepository productRepository,
            ProductDetailRepository productDetailRepository,
            CategoryRepository categoryRepository,
            BrandRepository brandRepository,
            ImageRepository imageRepository,
            ReviewRepository reviewRepository,
            CustomerRepository customerRepository,
            BillRepository billRepository,
            BillDetailRepository billDetailRepository,
            WishlistRepository wishlistRepository,
            WishlistDetailRepository wishlistDetailRepository) {
        this.productRepository = productRepository;
        this.productDetailRepository = productDetailRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.imageRepository = imageRepository;
        this.reviewRepository = reviewRepository;
        this.customerRepository = customerRepository;
        this.billRepository = billRepository;
        this.billDetailRepository = billDetailRepository;
        this.wishlistRepository = wishlistRepository;
        this.wishlistDetailRepository = wishlistDetailRepository;
    }

    // Trang chi tiết sản phẩm
    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable Integer id, HttpSession session, Model model) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) return "redirect:/products";

        // Không cho xem sản phẩm đã bị xóa mềm hoặc đang ẩn (status != 1)
        if (Boolean.TRUE.equals(product.getDeleteFlag())
                || product.getStatus() == null || product.getStatus() != 1) {
            return "redirect:/products";
        }

        List<ProductDetail> details = productDetailRepository
                .findByProductIdAndDeleteFlagFalse(id);

        // Lấy ảnh
        List<Image> images = imageRepository.findByProductId(id);

        // Lấy danh sách size, màu còn hàng
        List<String> sizes = details.stream()
                .filter(d -> d.getSize() != null && d.getQuantity() != null && d.getQuantity() > 0)
                .map(d -> d.getSize().getName())
                .distinct()
                .collect(Collectors.toList());

        List<String> colors = details.stream()
                .filter(d -> d.getColor() != null && d.getQuantity() != null && d.getQuantity() > 0)
                .map(d -> d.getColor().getName())
                .distinct()
                .collect(Collectors.toList());

        // Chọn biến thể đại diện (có giá final thấp nhất)
        ProductDetail representativeDetail = details.stream()
                .filter(d -> d.getQuantity() != null && d.getQuantity() > 0)
                .filter(d -> d.getFinalPrice() != null)
                .min(Comparator.comparing(ProductDetail::getFinalPrice))
                .orElseGet(() -> details.stream()
                        .filter(d -> d.getFinalPrice() != null)
                        .min(Comparator.comparing(ProductDetail::getFinalPrice))
                        .orElse(null));

        Double minPrice = representativeDetail != null && representativeDetail.getFinalPrice() != null ? (double) representativeDetail.getFinalPrice() : 0.0;
        Double minOriginalPrice = representativeDetail != null && representativeDetail.getPrice() != null ? (double) representativeDetail.getPrice() : 0.0;
        boolean hasActiveSale = representativeDetail != null && representativeDetail.isOnSale();

        model.addAttribute("product", product);
        model.addAttribute("details", details);
        model.addAttribute("sizes", sizes);
        model.addAttribute("colors", colors);
        model.addAttribute("images", images);
        model.addAttribute("mainImage", images.isEmpty() ? null : images.get(0));
        model.addAttribute("displayPrice", minPrice);
        model.addAttribute("displayOriginalPrice", minOriginalPrice);
        model.addAttribute("hasActiveSale", hasActiveSale);

        // === Đánh giá sản phẩm ===
        List<Review> reviews = reviewRepository.findByProduct_IdOrderByCreatedDateDesc(id);
        Double avgRating = reviewRepository.findAverageRatingByProductId(id);
        model.addAttribute("reviews", reviews);
        model.addAttribute("reviewCount", reviews.size());
        model.addAttribute("avgRating", avgRating != null ? avgRating : 0.0);

        // Kiểm tra khách hàng có thể đánh giá sản phẩm này không
        // (đã đăng nhập + đã mua sản phẩm trong đơn hoàn thành + chưa từng đánh giá)
        boolean canReview = false;
        Account account = (Account) session.getAttribute("account");
        if (account != null) {
            Customer customer = customerRepository.findByAccountId(account.getId());
            if (customer != null) {
                Review existingReview = reviewRepository.findByProduct_IdAndCustomer_Id(id, customer.getId());
                if (existingReview == null) {
                    List<Bill> bills = billRepository.findByCustomerId(customer.getId());
                    for (Bill bill : bills) {
                        if (bill.getStatus() != 7) continue; // chỉ đơn đã hoàn thành
                        List<BillDetail> billDetails = billDetailRepository.findByBillId(bill.getId());
                        boolean purchased = billDetails.stream()
                                .anyMatch(bd -> bd.getProductDetail() != null
                                        && bd.getProductDetail().getProduct() != null
                                        && bd.getProductDetail().getProduct().getId().equals(id));
                        if (purchased) {
                            canReview = true;
                            break;
                        }
                    }
                }
            }
        }
        model.addAttribute("canReview", canReview);

        // === Wishlist ===
        boolean inWishlist = false;
        int wishlistCount = 0;
        if (account != null) {
            Customer customer = customerRepository.findByAccountId(account.getId());
            if (customer != null) {
                Wishlist wishlist = wishlistRepository.findByCustomer_Id(customer.getId());
                if (wishlist != null) {
                    wishlistCount = wishlistDetailRepository.countByWishlist_Id(wishlist.getId());
                    WishlistDetail wd = wishlistDetailRepository.findByWishlist_IdAndProduct_Id(wishlist.getId(), id);
                    inWishlist = wd != null;
                }
            }
        }
        model.addAttribute("inWishlist", inWishlist);
        model.addAttribute("wishlistCount", wishlistCount);

        return "customer/product/detail";
    }

    // Trang danh sách sản phẩm + lọc
    @GetMapping("/products")
    public String products(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer brandId,
            @RequestParam(required = false) Integer gender,
            @RequestParam(required = false) String keyword,
            HttpSession session,
            Model model) {

        List<Product> products = productRepository.findByDeleteFlagFalse().stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .filter(p -> categoryId == null || (p.getCategory() != null && p.getCategory().getId().equals(categoryId)))
                .filter(p -> brandId == null || (p.getBrand() != null && p.getBrand().getId().equals(brandId)))
                .filter(p -> gender == null || gender.equals(p.getGender()))
                .filter(p -> keyword == null || keyword.isBlank()
                        || p.getName().toLowerCase().contains(keyword.toLowerCase()))
                .collect(Collectors.toList());

        // Lấy giá thấp nhất và ảnh cho mỗi sản phẩm
        Map<Integer, Double> productPrices = new HashMap<>();
        Map<Integer, Double> productOriginalPrices = new HashMap<>();
        Map<Integer, Boolean> productOnSale = new HashMap<>();
        Map<Integer, String> productImages = new HashMap<>();

        for (Product p : products) {
            List<ProductDetail> details = productDetailRepository.findByProductIdAndDeleteFlagFalse(p.getId());

            // Chọn biến thể đại diện (có giá final thấp nhất)
            ProductDetail representativeDetail = details.stream()
                    .filter(d -> d.getQuantity() != null && d.getQuantity() > 0)
                    .filter(d -> d.getFinalPrice() != null)
                    .min(Comparator.comparing(ProductDetail::getFinalPrice))
                    .orElseGet(() -> details.stream()
                            .filter(d -> d.getFinalPrice() != null)
                            .min(Comparator.comparing(ProductDetail::getFinalPrice))
                            .orElse(null));

            Double minPrice = representativeDetail != null && representativeDetail.getFinalPrice() != null ? (double) representativeDetail.getFinalPrice() : 0.0;
            productPrices.put(p.getId(), minPrice);

            Double minOriginalPrice = representativeDetail != null && representativeDetail.getPrice() != null ? (double) representativeDetail.getPrice() : 0.0;
            productOriginalPrices.put(p.getId(), minOriginalPrice);

            productOnSale.put(p.getId(), representativeDetail != null && representativeDetail.isOnSale());

            List<Image> imgs = imageRepository.findByProductId(p.getId());
            if (!imgs.isEmpty()) {
                productImages.put(p.getId(), imgs.get(0).getLink());
            }
        }

        model.addAttribute("products", products);
        model.addAttribute("productPrices", productPrices);
        model.addAttribute("productOriginalPrices", productOriginalPrices);
        model.addAttribute("productOnSale", productOnSale);
        model.addAttribute("productImages", productImages);
        model.addAttribute("categories", categoryRepository.findByDeleteFlag(false));
        model.addAttribute("brands", brandRepository.findByDeleteFlag(false));
        model.addAttribute("selectedCategory", categoryId);
        model.addAttribute("selectedBrand", brandId);
        model.addAttribute("selectedGender", gender);
        model.addAttribute("keyword", keyword);

        // === Wishlist: danh sách productId đã yêu thích để hiển thị trạng thái tim ===
        java.util.Set<Integer> wishlistProductIds = new java.util.HashSet<>();
        int wishlistCount = 0;
        Account account = (Account) session.getAttribute("account");
        if (account != null) {
            Customer customer = customerRepository.findByAccountId(account.getId());
            if (customer != null) {
                Wishlist wishlist = wishlistRepository.findByCustomer_Id(customer.getId());
                if (wishlist != null) {
                    List<WishlistDetail> wds = wishlistDetailRepository.findByWishlist_Id(wishlist.getId());
                    wishlistCount = wds.size();
                    for (WishlistDetail wd : wds) {
                        if (wd.getProduct() != null) wishlistProductIds.add(wd.getProduct().getId());
                    }
                }
            }
        }
        model.addAttribute("wishlistProductIds", wishlistProductIds);
        model.addAttribute("wishlistCount", wishlistCount);

        return "customer/product/list";
    }
}