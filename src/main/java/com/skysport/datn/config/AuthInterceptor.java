package com.skysport.datn.config;

import com.skysport.datn.entity.Account;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.skysport.datn.enums.RoleName;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * AuthInterceptor — xử lý phân quyền theo role sau khi Spring Security đã xác thực.
 *
 * Spring Security lo: xác thực (username/password), chặn /admin/** và /staff/**
 * Interceptor này lo: lấy object Account từ session cho Thymeleaf, và double-check role.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String uri = request.getRequestURI();

        // Các URL public — Spring Security đã permit, interceptor bỏ qua
        if (isPublicUri(uri)) {
            return true;
        }

        // Lấy Account từ session (được set khi đăng nhập trong AuthController)
        Account account = (Account) request.getSession().getAttribute("account");

        // Nếu không có trong session, kiểm tra SecurityContext
        if (account == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()
                    || "anonymousUser".equals(auth.getPrincipal())) {
                response.sendRedirect("/login");
                return false;
            }
            // SecurityContext có auth nhưng session mất account — redirect login để set lại
            response.sendRedirect("/login");
            return false;
        }

        // Double-check quyền admin
        if (uri.startsWith("/admin")) {
            if (!RoleName.ADMIN.matches(account.getRole().getName())) {
                response.sendRedirect("/login");
                return false;
            }
        }

        // Double-check quyền staff
        if (uri.startsWith("/staff")) {
            if (!RoleName.STAFF.matches(account.getRole().getName())
                    && !RoleName.ADMIN.matches(account.getRole().getName())) {
                response.sendRedirect("/login");
                return false;
            }
        }

        return true;
    }

    private boolean isPublicUri(String uri) {
        return uri.equals("/")
                ||uri.equals("/login") || uri.equals("/logout")
                || uri.equals("/home") || uri.startsWith("/register")
                || uri.startsWith("/products")
                || uri.startsWith("/product")
                || uri.startsWith("/css") || uri.startsWith("/js")
                || uri.startsWith("/images")
                || uri.startsWith("/checkout")
                || uri.startsWith("/order")
                || uri.startsWith("/track-order")
                || uri.startsWith("/guest")
                || uri.startsWith("/cart")
                || uri.startsWith("/wishlist/toggle")
                || uri.startsWith("/wishlist/add")
                || uri.startsWith("/wishlist/remove")
                || uri.startsWith("/api/chatbot");
    }
}