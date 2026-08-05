package com.skysport.datn.repository;

import com.skysport.datn.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Integer> {  // Đổi Integer thành Long
    List<Account> findByRoleId(Integer roleId);
    Optional<Account> findByUsername(String username);
    Optional<Account> findByEmail(String email);  // Thêm method này
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}