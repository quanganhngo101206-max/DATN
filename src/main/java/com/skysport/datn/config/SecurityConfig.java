package com.skysport.datn.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.core.Authentication;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // CSRF: bật lại, dùng Cookie (Thymeleaf tự thêm token qua th:action)
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null); // cần cho Spring Boot 3+

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler)
                        // Bỏ qua CSRF cho API AJAX nội bộ (dùng header X-XSRF-TOKEN từ cookie)
                        .ignoringRequestMatchers(
                                "/checkout/apply-discount",
                                "/cart/**",
                                "/wishlist/**",
                                "/customer/review/**"
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        // Static resources
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico", "/uploads/**").permitAll()

                        // Trang công khai
                        .requestMatchers(
                                "/", "/home",
                                "/login", "/register",
                                "/products/**", "/product/**",
                                "/cart/**",
                                "/checkout/**",
                                "/order/**",
                                "/track-order/**",
                                "/guest/**",
                                "/wishlist/toggle/**", "/wishlist/add/**", "/wishlist/remove/**"
                        ).permitAll()

                        // Chỉ Admin
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // AJAX staff — cần trả 401 JSON thay vì redirect
                        .requestMatchers(
                                "/staff/order/search-customer",
                                "/staff/order/product-variants/**",
                                "/staff/order/validate-discount"
                        ).hasAnyRole("ADMIN", "STAFF")

                        // Admin và Staff
                        .requestMatchers("/staff/**").hasAnyRole("ADMIN", "STAFF")

                        // Đã đăng nhập (Customer, Staff, Admin đều được)
                        .anyRequest().authenticated()
                )

                // Tắt form login mặc định của Spring — dùng form tự viết
                .formLogin(form -> form.disable())

                // Tắt HTTP Basic
                .httpBasic(basic -> basic.disable())

                // Xử lý khi truy cập không đủ quyền
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendRedirect("/login")
                        )
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                            if (auth != null && auth.isAuthenticated()) {
                                // Đã login nhưng sai role → redirect về trang đúng của role
                                boolean isCustomer = auth.getAuthorities().stream()
                                        .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));
                                boolean isStaff = auth.getAuthorities().stream()
                                        .anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"));
                                boolean isAdmin = auth.getAuthorities().stream()
                                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

                                if (isAdmin) {
                                    response.sendRedirect("/admin/dashboard");
                                } else if (isStaff) {
                                    response.sendRedirect("/staff/dashboard");
                                } else {
                                    response.sendRedirect("/home");
                                }
                            } else {
                                response.sendRedirect("/login");
                            }
                        })
                );

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
//        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}