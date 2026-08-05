package com.skysport.datn.service;

import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.Staff;
import com.skysport.datn.enums.RoleName;
import com.skysport.datn.repository.AccountRepository;
import com.skysport.datn.repository.RoleRepository;
import com.skysport.datn.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepository staffRepository;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public List<Staff> findAll() {
        return staffRepository.findAll();
    }

    public Staff findById(Integer id) {
        return staffRepository.findById(id).orElse(null);
    }

    public void save(Staff staff, String username, String password) {
        if (username == null || username.isBlank()) {
            throw new RuntimeException("Tên đăng nhập không được để trống!");
        }
        if (accountRepository.existsByUsername(username)) {
            throw new RuntimeException("Tên đăng nhập đã tồn tại!");
        }
        if (staff.getEmail() != null && !staff.getEmail().isBlank()
                && accountRepository.existsByEmail(staff.getEmail())) {
            throw new RuntimeException("Email đã được sử dụng!");
        }

        Account account = new Account();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(password));
        account.setEmail(staff.getEmail());
        account.setStatus(1);
        account.setIsNonLocked(true);
        account.setCreateDate(LocalDateTime.now());
        account.setUpdateDate(LocalDateTime.now());
        // Dùng RoleName.STAFF.getId() thay cho magic number 2
        account.setRole(roleRepository.findById(RoleName.STAFF.getId()).orElse(null));
        account.setCode("ACC" + generateShortId());
        accountRepository.save(account);

        staff.setCode("NV" + generateShortId());
        staff.setAccount(account);
        staff.setStatus(1);
        staffRepository.save(staff);
    }

    public void update(Staff staff) {
        staffRepository.save(staff);
    }

    public void toggleStatus(Integer id) {
        Staff staff = findById(id);
        if (staff != null) {
            Account account = staff.getAccount();
            account.setStatus(account.getStatus() == 1 ? 0 : 1);
            accountRepository.save(account);
        }
    }

    // Mở khóa tài khoản bị auto-lock do đăng nhập sai quá nhiều lần
    public void unlockAccount(Integer id) {
        Staff staff = findById(id);
        if (staff != null && staff.getAccount() != null) {
            Account account = staff.getAccount();
            account.setIsNonLocked(true);
            account.setFailedAttempts(0);
            accountRepository.save(account);
        }
    }

    private String generateShortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}