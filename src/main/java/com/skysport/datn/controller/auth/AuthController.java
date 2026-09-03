package com.skysport.datn.controller.auth;

import com.skysport.datn.controller.customer.CartController;
import com.skysport.datn.dto.RegisterRequest;
import com.skysport.datn.entity.Account;
import com.skysport.datn.enums.RoleName;
import com.skysport.datn.repository.AccountRepository;
import com.skysport.datn.service.CartService;
import com.skysport.datn.service.RegisterService;
import jakarta.servlet.http.HttpServletRequest;
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

    // Số lần đăng nhập sai tối đa trước khi khóa tài khoản
    private static final int MAX_FAILED_ATTEMPTS = 5;

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam(required = false, defaultValue = "customer") String type,
                        HttpServletRequest request,
                        HttpSession session,
                        Model model) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            // Chống session fixation: cấp session ID mới sau khi xác thực thành công,
            // giữ nguyên các attribute đã có (giỏ hàng guest, ...)
            request.changeSessionId();

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

            // Đăng nhập thành công -> reset bộ đếm đăng nhập sai
            if (account.getFailedAttempts() != null && account.getFailedAttempts() > 0) {
                account.setFailedAttempts(0);
                accountRepository.save(account);
            }

            session.setAttribute("account", account);

            // Merge giỏ hàng guest vào session (giữ nguyên items, chỉ cập nhật tồn kho)
            mergeGuestCart(session);

            String roleName = account.getRole().getName();
            if ("admin".equals(type)) {
                if (RoleName.ADMIN.matches(roleName))       return "redirect:/admin/report/sales";
                if (RoleName.STAFF.matches(roleName))   return "redirect:/staff/bill";
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
            model.addAttribute("error", handleFailedAttempt(username));
        } catch (AuthenticationException e) {
            model.addAttribute("error", "Đăng nhập thất bại!");
        }
        return "customer/login";
    }

    /**
     * Tăng bộ đếm đăng nhập sai cho tài khoản. Nếu đạt ngưỡng MAX_FAILED_ATTEMPTS
     * thì khóa tài khoản (isNonLocked = false) — lần đăng nhập kế tiếp sẽ bị
     * Spring Security chặn ngay ở bước authenticate() với LockedException.
     * Trả về thông báo lỗi phù hợp để hiển thị cho người dùng.
     */
    private String handleFailedAttempt(String username) {
        Account account = accountRepository.findByUsername(username).orElse(null);
        if (account == null) {
            return "Sai tài khoản hoặc mật khẩu!";
        }

        int attempts = (account.getFailedAttempts() == null ? 0 : account.getFailedAttempts()) + 1;
        account.setFailedAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            account.setIsNonLocked(false);
            accountRepository.save(account);
            return "Tài khoản đã bị khóa do đăng nhập sai quá " + MAX_FAILED_ATTEMPTS + " lần!";
        }

        accountRepository.save(account);
        return "Sai tài khoản hoặc mật khẩu! Còn " + (MAX_FAILED_ATTEMPTS - attempts) + " lần thử.";
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