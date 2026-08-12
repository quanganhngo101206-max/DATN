package com.skysport.datn.config;

import com.skysport.datn.repository.BillRepository;
import com.skysport.datn.repository.ImportOrderRepository;
import com.skysport.datn.service.CustomerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class AdminModelAdvice {

    @Autowired
    private ImportOrderRepository importOrderRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private CustomerService customerService;

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

    /**
     * Số đơn hàng mới (status=1: Chờ xác nhận) chưa được xử lý.
     * Hiện badge đỏ ở mục "Đơn hàng" cho cả admin lẫn nhân viên.
     * Badge chỉ biến mất khi đơn được xử lý (đổi status khác 1).
     */
    @ModelAttribute("newOrderCount")
    public long newOrderCount(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/admin") || uri.startsWith("/staff")) {
            try {
                return billRepository.countByStatus(1);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Số khách hàng mới đăng ký mà admin chưa xem.
     * Hiện badge đỏ ở mục "Khách hàng" (chỉ bên admin).
     * Badge biến mất ngay khi admin click vào xem danh sách khách hàng.
     */
    @ModelAttribute("newCustomerCount")
    public long newCustomerCount(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/admin")) {
            try {
                return customerService.countNewCustomers();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
}