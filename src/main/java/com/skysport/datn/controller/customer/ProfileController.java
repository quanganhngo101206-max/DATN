package com.skysport.datn.controller.customer;

import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.AddressShipping;
import com.skysport.datn.entity.Customer;
import com.skysport.datn.exception.BusinessException;
import com.skysport.datn.repository.AccountRepository;
import com.skysport.datn.repository.AddressShippingRepository;
import com.skysport.datn.repository.CustomerRepository;
import com.skysport.datn.repository.ProvinceRepository;
import com.skysport.datn.repository.WardRepository;
import com.skysport.datn.entity.Ward;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final AddressShippingRepository addressShippingRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProvinceRepository provinceRepository;
    private final WardRepository wardRepository;

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) return "redirect:/login";
        Customer customer = customerRepository.findByAccountId(account.getId());
        model.addAttribute("customer", customer);
        model.addAttribute("account", account);
        model.addAttribute("provinces", provinceRepository.findAll());
        if (customer != null) {
            model.addAttribute("addresses", addressShippingRepository.findByCustomerIdOrderByIsDefaultDescIdAsc(customer.getId()));
        }
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
            if (!phoneNumber.equals(customer.getPhoneNumber())
                    && customerRepository.findByPhoneNumber(phoneNumber).isPresent()) {
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
            if (e instanceof BusinessException) {
                redirectAttributes.addFlashAttribute("error", e.getMessage());
            } else {
                log.error("...", e);
                redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra, vui lòng thử lại!");
            }
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

    @PostMapping("/profile/address/add")
    public String addAddress(
            @RequestParam String address,
            @RequestParam String receiverName,
            @RequestParam String receiverPhone,
            @RequestParam Integer provinceId,
            @RequestParam Integer wardId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) return "redirect:/login";

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
        if (provinceId == null || wardId == null) {
            redirectAttributes.addFlashAttribute("errorAddress", "Vui lòng chọn Tỉnh/Thành và Phường/Xã!");
            return "redirect:/profile";
        }
        Ward ward = wardRepository.findById(wardId).orElse(null);
        if (ward == null || ward.getProvinceId() == null || !ward.getProvinceId().equals(provinceId)) {
            redirectAttributes.addFlashAttribute("errorAddress", "Phường/xã không thuộc tỉnh/thành đã chọn!");
            return "redirect:/profile";
        }
        try {
            Customer customer = customerRepository.findByAccountId(account.getId());
            if (customer == null) {
                redirectAttributes.addFlashAttribute("errorAddress", "Không tìm thấy thông tin khách hàng!");
                return "redirect:/profile";
            }
            List<AddressShipping> addresses = addressShippingRepository.findByCustomerIdOrderByIsDefaultDescIdAsc(customer.getId());
            AddressShipping addr = new AddressShipping();
            addr.setCustomer(customer);
            addr.setAddress(address.trim());
            addr.setReceiverName(receiverName.trim());
            addr.setReceiverPhone(receiverPhone.trim());
            addr.setProvinceId(provinceId);
            addr.setWardId(wardId);
            addr.setIsDefault(addresses.isEmpty());
            addr = addressShippingRepository.save(addr);
            if (addr.getIsDefault()) {
                customer.setAddressShipping(addr);
                customerRepository.save(customer);
            }
            redirectAttributes.addFlashAttribute("successAddress", "Thêm địa chỉ thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorAddress", "Có lỗi xảy ra: " + e.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/address/update")
    public String updateAddress(
            @RequestParam Integer id,
            @RequestParam String address,
            @RequestParam String receiverName,
            @RequestParam String receiverPhone,
            @RequestParam Integer provinceId,
            @RequestParam Integer wardId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) return "redirect:/login";

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
        if (provinceId == null || wardId == null) {
            redirectAttributes.addFlashAttribute("errorAddress", "Vui lòng chọn Tỉnh/Thành và Phường/Xã!");
            return "redirect:/profile";
        }
        Ward ward = wardRepository.findById(wardId).orElse(null);
        if (ward == null || ward.getProvinceId() == null || !ward.getProvinceId().equals(provinceId)) {
            redirectAttributes.addFlashAttribute("errorAddress", "Phường/xã không thuộc tỉnh/thành đã chọn!");
            return "redirect:/profile";
        }
        try {
            Customer customer = customerRepository.findByAccountId(account.getId());
            if (customer == null) {
                redirectAttributes.addFlashAttribute("errorAddress", "Không tìm thấy thông tin khách hàng!");
                return "redirect:/profile";
            }
            AddressShipping addr = addressShippingRepository.findById(id).orElse(null);
            if (addr == null || addr.getCustomer() == null
                    || !addr.getCustomer().getId().equals(customer.getId())) {
                redirectAttributes.addFlashAttribute("errorAddress", "Địa chỉ không tồn tại hoặc không thuộc tài khoản!");
                return "redirect:/profile";
            }
            addr.setAddress(address.trim());
            addr.setReceiverName(receiverName.trim());
            addr.setReceiverPhone(receiverPhone.trim());
            addr.setProvinceId(provinceId);
            addr.setWardId(wardId);
            addressShippingRepository.save(addr);
            if (Boolean.TRUE.equals(addr.getIsDefault())) {
                customer.setAddressShipping(addr);
                customerRepository.save(customer);
            }
            redirectAttributes.addFlashAttribute("successAddress", "Cập nhật địa chỉ thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorAddress", "Có lỗi xảy ra: " + e.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/address/default")
    public String setDefaultAddress(
            @RequestParam Integer id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) return "redirect:/login";
        try {
            Customer customer = customerRepository.findByAccountId(account.getId());
            if (customer == null) {
                redirectAttributes.addFlashAttribute("errorAddress", "Không tìm thấy thông tin khách hàng!");
                return "redirect:/profile";
            }
            AddressShipping selectedAddress = addressShippingRepository.findById(id).orElse(null);
            if (selectedAddress == null || selectedAddress.getCustomer() == null
                    || !selectedAddress.getCustomer().getId().equals(customer.getId())) {
                redirectAttributes.addFlashAttribute("errorAddress", "Địa chỉ không tồn tại hoặc không thuộc tài khoản!");
                return "redirect:/profile";
            }
            List<AddressShipping> addresses =
                    addressShippingRepository.findByCustomerIdOrderByIsDefaultDescIdAsc(customer.getId());
            for (AddressShipping addr : addresses) {
                addr.setIsDefault(addr.getId().equals(id));
            }
            addressShippingRepository.saveAll(addresses);
            customer.setAddressShipping(selectedAddress);
            customerRepository.save(customer);
            redirectAttributes.addFlashAttribute("successAddress", "Đã đặt địa chỉ mặc định!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorAddress", "Có lỗi xảy ra: " + e.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/address/delete")
    public String deleteAddress(
            @RequestParam Integer id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) return "redirect:/login";
        try {
            Customer customer = customerRepository.findByAccountId(account.getId());
            if (customer == null) {
                redirectAttributes.addFlashAttribute("errorAddress", "Không tìm thấy thông tin khách hàng!");
                return "redirect:/profile";
            }
            AddressShipping addr = addressShippingRepository.findById(id).orElse(null);
            if (addr == null || addr.getCustomer() == null
                    || !addr.getCustomer().getId().equals(customer.getId())) {
                redirectAttributes.addFlashAttribute("errorAddress", "Địa chỉ không tồn tại hoặc không thuộc tài khoản!");
                return "redirect:/profile";
            }
            boolean isDefault = Boolean.TRUE.equals(addr.getIsDefault());
            addressShippingRepository.delete(addr);
            if (isDefault) {
                List<AddressShipping> remaining =
                        addressShippingRepository.findByCustomerIdOrderByIsDefaultDescIdAsc(customer.getId());
                if (!remaining.isEmpty()) {
                    AddressShipping newDefault = remaining.get(0);
                    newDefault.setIsDefault(true);
                    addressShippingRepository.save(newDefault);
                    customer.setAddressShipping(newDefault);
                } else {
                    customer.setAddressShipping(null);
                }
                customerRepository.save(customer);
            }
            redirectAttributes.addFlashAttribute("successAddress", "Xóa địa chỉ thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorAddress", "Có lỗi xảy ra: " + e.getMessage());
        }
        return "redirect:/profile";
    }
}