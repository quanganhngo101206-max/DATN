package com.skysport.datn.controller.customer;

import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.AddressShipping;
import com.skysport.datn.entity.Customer;
import com.skysport.datn.repository.AccountRepository;
import com.skysport.datn.repository.AddressShippingRepository;
import com.skysport.datn.repository.CustomerRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
// KHÔNG dùng @Validated ở đây — tránh ConstraintViolationException khó xử lý với redirect
// Validation được làm thủ công bên dưới, rõ ràng hơn
public class ProfileController {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final AddressShippingRepository addressShippingRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) return "redirect:/login";
        Customer customer = customerRepository.findByAccountId(account.getId());
        model.addAttribute("customer", customer);
        model.addAttribute("account", account);
        return "customer/profile/index";
    }

    @PostMapping("/profile/update")
    public String updateProfile(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String phoneNumber,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Account account = (Account) session.getAttribute("account");
        if (account == null) return "redirect:/login";

        // Validate thủ công — tránh ConstraintViolationException làm GlobalExceptionHandler redirect sai
        if (name == null || name.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Họ tên không được để trống!");
            return "redirect:/profile";
        }
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            redirectAttributes.addFlashAttribute("error", "Email không đúng định dạng!");
            return "redirect:/profile";
        }
        if (phoneNumber == null || !phoneNumber.matches("^(0|\\+84)[3-9]\\d{8}$")) {
            redirectAttributes.addFlashAttribute("error", "Số điện thoại không hợp lệ!");
            return "redirect:/profile";
        }

        try {
            Customer customer = customerRepository.findByAccountId(account.getId());
            if (customer == null) {
                redirectAttributes.addFlashAttribute("error", "Không tìm thấy thông tin khách hàng!");
                return "redirect:/profile";
            }

            if (!email.equals(account.getEmail()) && accountRepository.findByEmail(email).isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Email này đã được sử dụng!");
                return "redirect:/profile";
            }

            if (!phoneNumber.equals(customer.getPhoneNumber()) && customerRepository.findByPhoneNumber(phoneNumber).isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Số điện thoại này đã được sử dụng!");
                return "redirect:/profile";
            }

            customer.setName(name.trim());
            customer.setEmail(email.trim());
            customer.setPhoneNumber(phoneNumber.trim());
            customerRepository.save(customer);

            account.setEmail(email.trim());
            account.setUpdateDate(LocalDateTime.now());
            accountRepository.save(account);
            session.setAttribute("account", account);

            redirectAttributes.addFlashAttribute("success", "Cập nhật thông tin thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra: " + e.getMessage());
        }

        return "redirect:/profile";
    }

    @PostMapping("/profile/change-password")
    public String changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Account account = (Account) session.getAttribute("account");
        if (account == null) return "redirect:/login";

        if (currentPassword == null || currentPassword.isBlank()) {
            redirectAttributes.addFlashAttribute("errorPassword", "Vui lòng nhập mật khẩu hiện tại!");
            return "redirect:/profile";
        }
        if (newPassword == null || newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("errorPassword", "Mật khẩu mới phải từ 6 ký tự trở lên!");
            return "redirect:/profile";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorPassword", "Mật khẩu xác nhận không khớp!");
            return "redirect:/profile";
        }

        try {
            Account dbAccount = accountRepository.findById(account.getId()).orElse(null);
            if (dbAccount == null) return "redirect:/login";

            if (!passwordEncoder.matches(currentPassword, dbAccount.getPassword())) {
                redirectAttributes.addFlashAttribute("errorPassword", "Mật khẩu hiện tại không đúng!");
                return "redirect:/profile";
            }

            dbAccount.setPassword(passwordEncoder.encode(newPassword));
            dbAccount.setUpdateDate(LocalDateTime.now());
            accountRepository.save(dbAccount);
            session.setAttribute("account", dbAccount);

            redirectAttributes.addFlashAttribute("successPassword", "Đổi mật khẩu thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorPassword", "Có lỗi xảy ra: " + e.getMessage());
        }

        return "redirect:/profile";
    }

    @PostMapping("/profile/update-address")
    public String updateAddress(
            @RequestParam String address,
            @RequestParam String receiverName,
            @RequestParam String receiverPhone,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Account account = (Account) session.getAttribute("account");
        if (account == null) return "redirect:/login";

        // Validate
        if (address == null || address.isBlank()) {
            redirectAttributes.addFlashAttribute("errorAddress", "Địa chỉ không được để trống!");
            return "redirect:/profile";
        }
        if (receiverName == null || receiverName.isBlank()) {
            redirectAttributes.addFlashAttribute("errorAddress", "Tên người nhận không được để trống!");
            return "redirect:/profile";
        }
        if (receiverPhone == null || !receiverPhone.matches("^(0|\\+84)[3-9]\\d{8}$")) {
            redirectAttributes.addFlashAttribute("errorAddress", "Số điện thoại người nhận không hợp lệ!");
            return "redirect:/profile";
        }

        try {
            Customer customer = customerRepository.findByAccountId(account.getId());
            if (customer == null) {
                redirectAttributes.addFlashAttribute("errorAddress", "Không tìm thấy thông tin khách hàng!");
                return "redirect:/profile";
            }

            // Lấy địa chỉ hiện tại hoặc tạo mới
            // FIX: phải save AddressShipping riêng trước, rồi mới set FK vào Customer
            AddressShipping addr = customer.getAddressShipping();
            if (addr == null) {
                addr = new AddressShipping();
                addr.setCustomer(customer);
            }
            addr.setAddress(address.trim());
            addr.setReceiverName(receiverName.trim());
            addr.setReceiverPhone(receiverPhone.trim());
            addr.setIsDefault(true);

            // Save AddressShipping trước (tạo row nếu mới)
            addr = addressShippingRepository.save(addr);

            // Cập nhật FK address_shipping_id trên Customer
            customer.setAddressShipping(addr);
            customerRepository.save(customer);

            redirectAttributes.addFlashAttribute("successAddress", "Cập nhật địa chỉ thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorAddress", "Có lỗi xảy ra: " + e.getMessage());
        }

        return "redirect:/profile";
    }
}