package com.skysport.datn.controller.customer;

import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.Customer;
import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.entity.Wishlist;
import com.skysport.datn.entity.WishlistDetail;
import com.skysport.datn.repository.CustomerRepository;
import com.skysport.datn.repository.WishlistDetailRepository;
import com.skysport.datn.repository.WishlistRepository;
import com.skysport.datn.service.CartService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.*;

@Controller
public class CartController {

    @Autowired
    private CartService cartService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private WishlistDetailRepository wishlistDetailRepository;

    private static final String CART_SESSION_KEY = "cart";

    // Lấy số lượng sản phẩm yêu thích của khách hàng đang đăng nhập
    private int getWishlistCount(HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) return 0;
        Customer customer = customerRepository.findByAccountId(account.getId());
        if (customer == null) return 0;
        Wishlist wishlist = wishlistRepository.findByCustomer_Id(customer.getId());
        if (wishlist == null) return 0;
        List<WishlistDetail> wds = wishlistDetailRepository.findByWishlist_Id(wishlist.getId());
        return wds.size();
    }

    // Lấy giỏ hàng từ session
    private Map<Integer, CartItem> getCartFromSession(HttpSession session) {
        Map<Integer, CartItem> cart = (Map<Integer, CartItem>) session.getAttribute(CART_SESSION_KEY);
        if (cart == null) {
            cart = new LinkedHashMap<>();
            session.setAttribute(CART_SESSION_KEY, cart);
        }
        return cart;
    }

    // Cập nhật số lượng giỏ hàng
    private void updateCartCount(HttpSession session) {
        Map<Integer, CartItem> cart = getCartFromSession(session);
        int totalQty = cart.values().stream().mapToInt(CartItem::getQuantity).sum();
        session.setAttribute("cartCount", totalQty);
    }

    // Thêm vào giỏ hàng (non-AJAX)
    @PostMapping("/cart/add")
    public String addToCart(@RequestParam Integer productId,
                            @RequestParam(required = false) String color,
                            @RequestParam(required = false) String size,
                            @RequestParam Integer quantity,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {

        ProductDetail detail = cartService.findProductDetail(productId, color, size);

        if (detail == null) {
            redirectAttributes.addFlashAttribute("error", "Sản phẩm không tồn tại!");
            return "redirect:/products/" + productId;
        }

        if (!cartService.checkStock(detail, quantity)) {
            redirectAttributes.addFlashAttribute("error", "Số lượng sản phẩm không đủ! Tồn kho: " + detail.getQuantity());
            return "redirect:/products/" + productId;
        }

        Map<Integer, CartItem> cart = getCartFromSession(session);

        if (cart.containsKey(detail.getId())) {
            CartItem item = cart.get(detail.getId());
            int newQty = item.getQuantity() + quantity;
            if (!cartService.checkStock(detail, newQty)) {
                redirectAttributes.addFlashAttribute("error", "Vượt quá số lượng tồn kho!");
                return "redirect:/products/" + productId;
            }
            item.setQuantity(newQty);
        } else {
            CartItem item = new CartItem();
            item.setProductDetailId(detail.getId());
            item.setProductId(productId);
            item.setProductName(detail.getProduct().getName());
            // ✅ SỬA DÒNG NÀY - chỉ lấy giá từ ProductDetail
            item.setPrice(detail.getFinalPrice() != null ? detail.getFinalPrice().doubleValue() : 0.0);
            item.setQuantity(quantity);
            item.setColor(color);
            item.setSize(size);
            item.setImageUrl(cartService.getProductImage(productId));
            cart.put(detail.getId(), item);
        }

        updateCartCount(session);
        redirectAttributes.addFlashAttribute("success", "Đã thêm sản phẩm vào giỏ hàng!");
        return "redirect:/products/" + productId;
    }

    // Xem giỏ hàng
    @GetMapping("/cart")
    public String viewCart(HttpSession session, Model model) {
        Map<Integer, CartItem> cart = getCartFromSession(session);
        
        // Cập nhật lại giá cho các sản phẩm trong giỏ
        for (CartItem item : cart.values()) {
            ProductDetail detail = cartService.getProductDetailById(item.getProductDetailId());
            if (detail != null) {
                item.setPrice(detail.getFinalPrice() != null ? detail.getFinalPrice().doubleValue() : 0.0);
            }
        }

        List<CartItem> items = new ArrayList<>(cart.values());

        double subtotal = items.stream().mapToDouble(i -> i.getPrice() * i.getQuantity()).sum();
        double shipping = subtotal >= 500000 ? 0 : 30000;
        double total = subtotal + shipping;

        model.addAttribute("cartItems", items);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("shipping", shipping);
        model.addAttribute("total", total);
        model.addAttribute("cartCount", session.getAttribute("cartCount"));
        model.addAttribute("wishlistCount", getWishlistCount(session));

        return "customer/cart/index";
    }

    // Cập nhật số lượng (AJAX)
    @PostMapping("/cart/update")
    @ResponseBody
    public Map<String, Object> updateQuantity(@RequestParam Integer detailId,
                                              @RequestParam Integer quantity,
                                              HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Map<Integer, CartItem> cart = getCartFromSession(session);

        if (!cart.containsKey(detailId)) {
            result.put("success", false);
            result.put("message", "Sản phẩm không tồn tại trong giỏ!");
            return result;
        }

        ProductDetail detail = cartService.getProductDetailById(detailId);
        if (detail == null) {
            result.put("success", false);
            result.put("message", "Sản phẩm không còn tồn tại!");
            return result;
        }
        // Kiểm tra tồn kho thực tế (quantity là số mới muốn set, không phải số thêm vào)
        if (!cartService.checkStock(detail, quantity)) {
            result.put("success", false);
            result.put("message", "Số lượng vượt quá tồn kho! Chỉ còn " + detail.getQuantity() + " sản phẩm.");
            return result;
        }

        CartItem item = cart.get(detailId);
        if (quantity <= 0) {
            cart.remove(detailId);
        } else {
            item.setQuantity(quantity);
            // Cập nhật lại giá
            item.setPrice(detail.getFinalPrice() != null ? detail.getFinalPrice().doubleValue() : 0.0);
        }

        updateCartCount(session);

        double subtotal = cart.values().stream().mapToDouble(i -> i.getPrice() * i.getQuantity()).sum();
        double shipping = subtotal >= 500000 ? 0 : 30000;

        // Tính lại thành tiền của sản phẩm
        double itemTotal = 0;
        double unitPrice = 0;
        if (cart.containsKey(detailId)) {
            unitPrice = item.getPrice();
            itemTotal = unitPrice * quantity;
        }

        result.put("success", true);
        result.put("subtotal", subtotal);
        result.put("shipping", shipping);
        result.put("total", subtotal + shipping);
        result.put("cartCount", session.getAttribute("cartCount"));
        result.put("itemTotal", itemTotal);  // Thành tiền sản phẩm
        result.put("unitPrice", unitPrice);   // Đơn giá
        result.put("detailId", detailId);

        return result;
    }

    // Xóa sản phẩm khỏi giỏ
    @GetMapping("/cart/remove/{detailId}")
    public String removeFromCart(@PathVariable Integer detailId, HttpSession session, RedirectAttributes redirectAttributes) {
        Map<Integer, CartItem> cart = getCartFromSession(session);
        cart.remove(detailId);
        updateCartCount(session);
        redirectAttributes.addFlashAttribute("success", "Đã xóa sản phẩm khỏi giỏ hàng!");
        return "redirect:/cart";
    }

    // AJAX thêm vào giỏ hàng
    @PostMapping("/cart/add-ajax")
    @ResponseBody
    public Map<String, Object> addToCartAjax(@RequestParam Integer productId,
                                             @RequestParam(required = false) String color,
                                             @RequestParam(required = false) String size,
                                             @RequestParam Integer quantity,
                                             HttpSession session) {
        Map<String, Object> result = new HashMap<>();





        ProductDetail detail = cartService.findProductDetail(productId, color, size);

        if (detail == null) {
            result.put("success", false);
            result.put("message", "Không tìm thấy sản phẩm với màu/size này!");
            return result;
        }


        if (!cartService.checkStock(detail, quantity)) {
            result.put("success", false);
            result.put("message", "Số lượng không đủ! Tồn kho: " + detail.getQuantity());
            return result;
        }

        Map<Integer, CartItem> cart = getCartFromSession(session);

        if (cart.containsKey(detail.getId())) {
            CartItem item = cart.get(detail.getId());
            int newQty = item.getQuantity() + quantity;
            if (!cartService.checkStock(detail, newQty)) {
                result.put("success", false);
                result.put("message", "Vượt quá số lượng tồn kho!");
                return result;
            }
            item.setQuantity(newQty);
            result.put("message", "Đã cập nhật số lượng!");
        } else {
            CartItem item = new CartItem();
            item.setProductDetailId(detail.getId());
            item.setProductId(productId);
            item.setProductName(detail.getProduct().getName());
            // ✅ SỬA DÒNG NÀY - chỉ lấy giá từ ProductDetail
            item.setPrice(detail.getFinalPrice() != null ? detail.getFinalPrice().doubleValue() : 0.0);
            item.setQuantity(quantity);
            item.setColor(color);
            item.setSize(size);
            item.setImageUrl(cartService.getProductImage(productId));
            cart.put(detail.getId(), item);
            result.put("message", "Đã thêm sản phẩm vào giỏ!");
        }

        updateCartCount(session);

        result.put("success", true);
        result.put("cartCount", session.getAttribute("cartCount"));
        return result;
    }

    @DeleteMapping("/cart/remove/{detailId}")
    @ResponseBody
    public Map<String, Object> removeFromCartAjax(@PathVariable Integer detailId,
                                                  HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Map<Integer, CartItem> cart = getCartFromSession(session);

        if (cart.containsKey(detailId)) {
            cart.remove(detailId);
            updateCartCount(session);

            // Tính toán lại các giá trị
            double subtotal = cart.values().stream()
                    .mapToDouble(i -> i.getPrice() * i.getQuantity())
                    .sum();
            double shipping = subtotal >= 500000 ? 0 : 30000;
            double total = subtotal + shipping;

            result.put("success", true);
            result.put("message", "Đã xóa sản phẩm!");
            result.put("cartCount", session.getAttribute("cartCount"));
            result.put("subtotal", subtotal);
            result.put("shipping", shipping);
            result.put("total", total);
            result.put("discountAmount", 0); // Nếu có giảm giá thì tính thêm
        } else {
            result.put("success", false);
            result.put("message", "Sản phẩm không tồn tại!");
        }

        return result;
    }

    // Inner class CartItem
    public static class CartItem {
        private Integer productDetailId;
        private Integer productId;
        private String productName;
        private Double price;
        private Integer quantity;
        private String color;
        private String size;
        private String imageUrl;

        public Integer getProductDetailId() { return productDetailId; }
        public void setProductDetailId(Integer productDetailId) { this.productDetailId = productDetailId; }
        public Integer getProductId() { return productId; }
        public void setProductId(Integer productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public String getSize() { return size; }
        public void setSize(String size) { this.size = size; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public Double getTotalPrice() { return price * quantity; }
    }
}