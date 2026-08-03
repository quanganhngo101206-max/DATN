package com.skysport.datn.controller.auth;

import com.skysport.datn.controller.customer.CartController;
import com.skysport.datn.dto.RegisterRequest;
import com.skysport.datn.entity.Account;
import com.skysport.datn.enums.RoleName;
import com.skysport.datn.repository.AccountRepository;
import com.skysport.datn.service.CartService;
import com.skysport.datn.service.RegisterService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final RegisterService registerService;
    private final AuthenticationManager authenticationManager;
    private final AccountRepository accountRepository;
    private final CartService cartService;

    private static final String CART_SESSION_KEY = "cart";

    /**
     * Sau khi đăng nhập, kiểm tra lại tồn kho từng item trong giỏ guest.
     * Nếu vượt tồn kho → điều chỉnh. Nếu hết hàng → xóa khỏi giỏ.
     * Cập nhật lại giá mới nhất cho từng item.
     */
    @SuppressWarnings("unchecked")
    private void mergeGuestCart(HttpSession session) {
        Map<Integer, CartController.CartItem> cart =
                (Map<Integer, CartController.CartItem>) session.getAttribute(CART_SESSION_KEY);
        if (cart == null || cart.isEmpty()) return;

        Map<Integer, CartController.CartItem> merged = new LinkedHashMap<>();
        for (Map.Entry<Integer, CartController.CartItem> entry : cart.entrySet()) {
            CartController.CartItem item = entry.getValue();
            var detail = cartService.getProductDetailById(item.getProductDetailId());

            if (detail == null || detail.getQuantity() == null || detail.getQuantity() <= 0) {
                continue; // Sản phẩm hết hàng → bỏ
            }
            if (item.getQuantity() > detail.getQuantity()) {
                item.setQuantity(detail.getQuantity()); // Giảm về tồn kho thực tế
            }
            // Cập nhật giá mới nhất
            item.setPrice(detail.getPrice() != null ? detail.getPrice().doubleValue() : 0.0);
            merged.put(entry.getKey(), item);
        }

        session.setAttribute(CART_SESSION_KEY, merged);
        int totalQty = merged.values().stream().mapToInt(CartController.CartItem::getQuantity).sum();
        session.setAttribute("cartCount", totalQty);
    }

    @GetMapping("/login")
    public String loginPage() {
        return "customer/login";
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/home";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam(required = false, defaultValue = "customer") String type,
                        HttpSession session,
                        Model model) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext()
            );

            Account account = accountRepository.findByUsername(username).orElse(null);
            if (account == null) {
                model.addAttribute("error", "Không tìm thấy tài khoản!");
                return "customer/login";
            }
            session.setAttribute("account", account);

            // Merge giỏ hàng guest vào session (giữ nguyên items, chỉ cập nhật tồn kho)
            mergeGuestCart(session);

            String roleName = account.getRole().getName();
            if ("admin".equals(type)) {
                if (RoleName.ADMIN.matches(roleName))       return "redirect:/admin/dashboard";
                if (RoleName.STAFF.matches(roleName))   return "redirect:/staff/dashboard";
                model.addAttribute("error", "Bạn không có quyền truy cập!");
                return "customer/login";
            } else {
                if (RoleName.CUSTOMER.matches(roleName))  return "redirect:/home";
                model.addAttribute("error", "Bạn không có quyền truy cập!");
                return "customer/login";
            }

        } catch (DisabledException | LockedException e) {
            model.addAttribute("error", "Tài khoản đã bị khóa!");
        } catch (BadCredentialsException e) {
            model.addAttribute("error", "Sai tài khoản hoặc mật khẩu!");
        } catch (AuthenticationException e) {
            model.addAttribute("error", "Đăng nhập thất bại!");
        }
        return "customer/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "customer/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterRequest request,
                           BindingResult bindingResult,
                           @RequestParam String confirmPassword,
                           Model model) {

        // Validate annotation trước
        if (bindingResult.hasErrors()) {
            model.addAttribute("registerRequest", request);
            return "customer/register";
        }

        // Validate confirm password
        if (!request.getPassword().equals(confirmPassword)) {
            bindingResult.rejectValue("password", "password.mismatch", "Mật khẩu xác nhận không khớp!");
            model.addAttribute("registerRequest", request);
            return "customer/register";
        }

        try {
            registerService.register(request);
            model.addAttribute("success", "Đăng ký thành công! Vui lòng đăng nhập.");
            return "customer/login";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("registerRequest", request);
            return "customer/register";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        Account account = (Account) session.getAttribute("account");
        String roleName = (account != null && account.getRole() != null) ? account.getRole().getName() : null;

        SecurityContextHolder.clearContext();
        session.invalidate();

        if (RoleName.ADMIN.matches(roleName) || RoleName.STAFF.matches(roleName)) {
            return "redirect:/login";
        }
        return "redirect:/home";
    }
}