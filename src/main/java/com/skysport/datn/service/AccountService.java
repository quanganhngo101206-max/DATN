package com.skysport.datn.service;

import com.skysport.datn.entity.Account;
import com.skysport.datn.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    // ✅ Sửa lại để trả về null thay vì throw exception
    public Account findByUsername(String username) {
        Optional<Account> account = accountRepository.findByUsername(username);
        return account.orElse(null);  // Trả về null nếu không tìm thấy
    }

    // ✅ Thêm method kiểm tra tồn tại
    public boolean existsByUsername(String username) {
        return accountRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return accountRepository.existsByEmail(email);
    }
}