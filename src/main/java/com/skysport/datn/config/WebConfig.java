package com.skysport.datn.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

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
                        "/css/**", "/js/**", "/images/**", "/favicon.ico",
                        // AJAX endpoints bán tại quầy — AuthInterceptor không xử lý được
                        // vì chúng trả JSON (không redirect được), Spring Security đã bảo vệ qua hasRole
                        "/staff/order/search-customer",
                        "/staff/order/product-variants/**",
                        "/staff/order/validate-discount"
                );
    }
}