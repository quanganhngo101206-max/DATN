package com.skysport.datn.service;

import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.Customer;
import com.skysport.datn.repository.AccountRepository;
import com.skysport.datn.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    private final AccountRepository accountRepository;

    // Lấy tất cả khách hàng
    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    // Tìm theo id
    public Customer findById(Integer id) {
        return customerRepository.findById(id).orElse(null);
    }

    // Khóa/Mở khóa tài khoản
    public void toggleStatus(Integer id) {
        Customer customer = findById(id);
        if (customer != null) {
            Account account = customer.getAccount();
            account.setStatus(account.getStatus() == 1 ? 0 : 1);
            accountRepository.save(account);
        }
    }

    // Mở khóa tài khoản bị auto-lock do đăng nhập sai quá nhiều lần
    public void unlockAccount(Integer id) {
        Customer customer = findById(id);
        if (customer != null && customer.getAccount() != null) {
            Account account = customer.getAccount();
            account.setIsNonLocked(true);
            account.setFailedAttempts(0);
            accountRepository.save(account);
        }
    }

    // Tìm kiếm theo tên hoặc email
    public List<Customer> search(String keyword) {
        return customerRepository.searchByKeyword(keyword);
    }

    public Page<Customer> findAllPaged(int page, int size) {
        return customerRepository.findAll(PageRequest.of(page, size));
    }

    public Page<Customer> searchPaged(String keyword, int page, int size) {
        return customerRepository.searchByKeyword(keyword, PageRequest.of(page, size));
    }
}