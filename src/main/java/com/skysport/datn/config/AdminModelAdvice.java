package com.skysport.datn.config;

import com.skysport.datn.repository.ImportOrderRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class AdminModelAdvice {

    @Autowired
    private ImportOrderRepository importOrderRepository;

    /**
     * Tự động inject pendingCount vào model của mọi trang /admin/**
     * Sidebar fragment dùng biến này để hiển thị badge phiếu nhập chờ duyệt.
     */
    @ModelAttribute("pendingCount")
    public int pendingCount(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/admin") || uri.startsWith("/staff/import")) {
            try {
                return importOrderRepository.findByStatus(1).size();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
}