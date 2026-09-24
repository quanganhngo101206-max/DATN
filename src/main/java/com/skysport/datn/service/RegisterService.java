package com.skysport.datn.service;

import com.skysport.datn.dto.RegisterRequest;
import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.Customer;
import com.skysport.datn.entity.Role;
import com.skysport.datn.enums.RoleName;
import com.skysport.datn.repository.AccountRepository;
import com.skysport.datn.repository.CustomerRepository;
import com.skysport.datn.repository.RoleRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import com.skysport.datn.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class RegisterService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void register(RegisterRequest request) {

        if (accountRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new BusinessException("Tên đăng nhập đã tồn tại!");
        }
        if (accountRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessException("Email đã được đăng ký!");
        }
        if (customerRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new BusinessException("Số điện thoại đã được đăng ký!");
        }

        Role customerRole = roleRepository.findById(RoleName.CUSTOMER.getId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy role khách hàng"));

        Account account = new Account();
        account.setUsername(request.getUsername());
        account.setPassword(passwordEncoder.encode(request.getPassword()));
        account.setEmail(request.getEmail());
        account.setRole(customerRole);
        account.setStatus(1);
        account.setCreateDate(LocalDateTime.now());
        account.setUpdateDate(LocalDateTime.now());
        account.setIsNonLocked(true);
        account.setFailedAttempts(0);
        account.setCode("ACC" + generateShortId());

        accountRepository.save(account);

        Customer customer = new Customer();
        customer.setName(request.getFullName());
        customer.setEmail(request.getEmail());
        customer.setPhoneNumber(request.getPhoneNumber());
        customer.setAccount(account);
        customer.setCode("KH" + generateShortId());
        customerRepository.save(customer);
    }

    /**
     * Sinh ID ngắn 8 ký tự từ UUID — không phụ thuộc vào COUNT(*).
     * Xác suất trùng cực thấp (1/16^8 ≈ 1/4 tỷ), đủ cho hệ thống DATN.
     * Nếu cần đảm bảo 100%: thêm @Column(unique=true) lên code và retry khi duplicate.
     */
    private String generateShortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}