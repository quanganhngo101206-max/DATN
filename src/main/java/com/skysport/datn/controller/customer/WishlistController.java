package com.skysport.datn.controller.customer;

import com.skysport.datn.entity.*;
import com.skysport.datn.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class WishlistController {

    @Autowired private WishlistRepository wishlistRepository;
    @Autowired private WishlistDetailRepository wishlistDetailRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductDetailRepository productDetailRepository;
    @Autowired private ImageRepository imageRepository;

    // Lấy (hoặc tạo) wishlist cho customer hiện tại; trả null nếu chưa đăng nhập / không phải customer
    private Wishlist getOrCreateWishlist(HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) return null;

        Customer customer = customerRepository.findByAccountId(account.getId());
        if (customer == null) return null;

        Wishlist wishlist = wishlistRepository.findByCustomer_Id(customer.getId());
        if (wishlist == null) {
            wishlist = Wishlist.builder().customer(customer).build();
            wishlist = wishlistRepository.save(wishlist);
        }
        return wishlist;
    }

    // Trang danh sách yêu thích
    @GetMapping("/wishlist")
    public String wishlistPage(HttpSession session, Model model) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) return "redirect:/login";

        Wishlist wishlist = getOrCreateWishlist(session);
        List<WishlistDetail> wishlistDetails = wishlist != null
                ? wishlistDetailRepository.findByWishlist_Id(wishlist.getId())
                : List.of();

        // Lấy giá thấp nhất & ảnh đại diện cho mỗi sản phẩm
        Map<Integer, Double> productPrices = new HashMap<>();
        Map<Integer, String> productImages = new HashMap<>();

        for (WishlistDetail wd : wishlistDetails) {
            Product p = wd.getProduct();
            if (p == null) continue;

            List<ProductDetail> details = productDetailRepository.findByProductIdAndDeleteFlagFalse(p.getId());
            Double minPrice = details.stream()
                    .filter(d -> d.getPrice() != null)
                    .mapToDouble(ProductDetail::getPrice)
                    .min()
                    .orElse(0.0);
            productPrices.put(p.getId(), minPrice);

            List<Image> imgs = imageRepository.findByProductId(p.getId());
            if (!imgs.isEmpty()) {
                productImages.put(p.getId(), imgs.get(0).getLink());
            }
        }

        model.addAttribute("wishlistDetails", wishlistDetails);
        model.addAttribute("productPrices", productPrices);
        model.addAttribute("productImages", productImages);
        model.addAttribute("wishlistCount", wishlistDetails.size());
        return "customer/wishlist/index";
    }

    // Thêm sản phẩm vào wishlist
    @PostMapping("/wishlist/add/{productId}")
    @Transactional
    @ResponseBody
    public Map<String, Object> add(@PathVariable Integer productId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Account account = (Account) session.getAttribute("account");
        if (account == null) {
            result.put("success", false);
            result.put("message", "Vui lòng đăng nhập để sử dụng chức năng yêu thích!");
            return result;
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            result.put("success", false);
            result.put("message", "Sản phẩm không tồn tại!");
            return result;
        }

        Wishlist wishlist = getOrCreateWishlist(session);
        WishlistDetail existing = wishlistDetailRepository.findByWishlist_IdAndProduct_Id(wishlist.getId(), productId);
        if (existing == null) {
            WishlistDetail wd = WishlistDetail.builder()
                    .wishlist(wishlist)
                    .product(product)
                    .build();
            wishlistDetailRepository.save(wd);
        }

        int count = wishlistDetailRepository.countByWishlist_Id(wishlist.getId());
        result.put("success", true);
        result.put("inWishlist", true);
        result.put("wishlistCount", count);
        result.put("message", "Đã thêm vào danh sách yêu thích!");
        return result;
    }

    // Xóa sản phẩm khỏi wishlist
    @PostMapping("/wishlist/remove/{productId}")
    @Transactional
    @ResponseBody
    public Map<String, Object> remove(@PathVariable Integer productId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Account account = (Account) session.getAttribute("account");
        if (account == null) {
            result.put("success", false);
            result.put("message", "Vui lòng đăng nhập!");
            return result;
        }

        Wishlist wishlist = getOrCreateWishlist(session);
        wishlistDetailRepository.deleteByWishlist_IdAndProduct_Id(wishlist.getId(), productId);

        int count = wishlistDetailRepository.countByWishlist_Id(wishlist.getId());
        result.put("success", true);
        result.put("inWishlist", false);
        result.put("wishlistCount", count);
        result.put("message", "Đã xóa khỏi danh sách yêu thích!");
        return result;
    }

    // Bật/tắt yêu thích (dùng cho nút trái tim trên trang sản phẩm)
    @PostMapping("/wishlist/toggle/{productId}")
    @Transactional
    @ResponseBody
    public Map<String, Object> toggle(@PathVariable Integer productId, HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Vui lòng đăng nhập để sử dụng chức năng yêu thích!");
            return result;
        }

        Wishlist wishlist = getOrCreateWishlist(session);
        WishlistDetail existing = wishlistDetailRepository.findByWishlist_IdAndProduct_Id(wishlist.getId(), productId);

        if (existing != null) {
            wishlistDetailRepository.delete(existing);
        } else {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "Sản phẩm không tồn tại!");
                return result;
            }
            WishlistDetail wd = WishlistDetail.builder()
                    .wishlist(wishlist)
                    .product(product)
                    .build();
            wishlistDetailRepository.save(wd);
        }

        int count = wishlistDetailRepository.countByWishlist_Id(wishlist.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("inWishlist", existing == null); // true nếu vừa thêm
        result.put("wishlistCount", count);
        result.put("message", existing == null ? "Đã thêm vào danh sách yêu thích!" : "Đã xóa khỏi danh sách yêu thích!");
        return result;
    }
}