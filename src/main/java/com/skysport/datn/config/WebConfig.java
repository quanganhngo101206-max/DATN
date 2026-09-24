package com.skysport.datn.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/",
                        "/login", "/logout", "/register",
                        "/home",
                        "/products", "/products/**",
                        "/cart", "/cart/**",
                        "/checkout", "/checkout/**",
                        "/order/**",
                        "/track-order", "/track-order/**",
                        "/guest/**",
                        "/css/**", "/js/**", "/images/**", "/favicon.ico", "/uploads/**",

                        // Mock VNPay: điện thoại quét QR không cần đăng nhập
                        "/mock-vnpay", "/mock-vnpay/**", "/mock-vnpay-success",
                        // AJAX endpoints bán tại quầy — AuthInterceptor không xử lý được
                        // vì chúng trả JSON (không redirect được), Spring Security đã bảo vệ qua hasRole
                        "/staff/order/search-customer",
                        "/staff/order/product-variants/**",
                        "/staff/order/validate-discount"
                );
    }

    @Override
    public void addResourceHandlers(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }
}