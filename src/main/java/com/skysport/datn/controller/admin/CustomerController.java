package com.skysport.datn.controller.admin;

import com.skysport.datn.entity.AddressShipping;
import com.skysport.datn.entity.Customer;
import com.skysport.datn.entity.Ward;
import com.skysport.datn.repository.ProvinceRepository;
import com.skysport.datn.repository.WardRepository;
import com.skysport.datn.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@Controller
@RequestMapping("/admin/customer")
@RequiredArgsConstructor
public class CustomerController {
    private final ProvinceRepository provinceRepository;
    // ĐÃ XÓA: DistrictRepository — Ward map thẳng lên Province
    private final WardRepository wardRepository;
    private final CustomerService customerService;
    private static final int PAGE_SIZE = 10;
    // 1. DANH SÁCH KHÁCH HÀNG
    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<?> customerPage;
        if (keyword != null && !keyword.isBlank()) {
            customerPage = customerService.searchPaged(keyword, page, PAGE_SIZE);
            model.addAttribute("keyword", keyword);
        } else {
            customerPage = customerService.findAllPaged(page, PAGE_SIZE);
        }
        model.addAttribute("customers", customerPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", customerPage.getTotalPages());
        model.addAttribute("totalItems", customerPage.getTotalElements());
        return "admin/customer/list";
    }
    // 2. CHI TIẾT KHÁCH HÀNG
    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        Customer customer = customerService.findById(id);
        model.addAttribute("customer", customer);
        model.addAttribute("provinces", provinceRepository.findAll());
        if (customer.getAddressShipping() != null) {
            AddressShipping address = customer.getAddressShipping();
            if (address.getProvinceId() != null) {
                model.addAttribute("province", provinceRepository.findById(address.getProvinceId()).orElse(null));
            }
            // ĐÃ XÓA: getDistrictId() — không tồn tại trong AddressShipping
            if (address.getWardId() != null) {
                model.addAttribute("ward", wardRepository.findById(address.getWardId()).orElse(null));
            }
        }
        return "admin/customer/detail";
    }
    // 3. API: Lấy danh sách Phường/Xã theo Tỉnh (bỏ qua Huyện)
    // Đường dẫn: /admin/customer/api/wards/79
    @GetMapping("/api/wards/{provinceId}")
    @ResponseBody
    public List<Ward> getWardsByProvince(@PathVariable Integer provinceId) {
        return wardRepository.findByProvinceIdOrderByNameAsc(provinceId);
    }
    // 4. THAY ĐỔI TRẠNG THÁI KHÁCH HÀNG
    @GetMapping("/toggle/{id}")
    public String toggle(@PathVariable Integer id) {
        customerService.toggleStatus(id);
        return "redirect:/admin/customer";
    }

    // 5. Mở khóa tài khoản bị auto-lock do đăng nhập sai quá nhiều lần
    @GetMapping("/unlock/{id}")
    public String unlock(@PathVariable Integer id) {
        customerService.unlockAccount(id);
        return "redirect:/admin/customer";
    }
}