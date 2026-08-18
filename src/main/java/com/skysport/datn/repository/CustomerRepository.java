package com.skysport.datn.repository;

import com.skysport.datn.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Integer> {
    List<Customer> findAll();

    // Phân trang
    Page<Customer> findAll(Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.name LIKE %:kw% OR c.email LIKE %:kw% OR c.phoneNumber LIKE %:kw% OR c.code LIKE %:kw%")
    Page<Customer> searchByKeyword(@Param("kw") String keyword, Pageable pageable);
    Customer findByAccountId(Integer accountId);
    Optional<Customer> findByPhoneNumber(String phoneNumber);

    @Query("SELECT MAX(c.id) FROM Customer c")
    Integer findMaxId();

    @Query("SELECT c FROM Customer c WHERE c.name LIKE %:kw% OR c.email LIKE %:kw% OR c.phoneNumber LIKE %:kw% OR c.code LIKE %:kw%")
    List<Customer> searchByKeyword(@Param("kw") String keyword);

    // Đếm số khách hàng có tài khoản tạo sau một mốc thời gian cho trước
    // (dùng account.createDate có sẵn, không cần thêm cột mới cho Customer)
    long countByAccount_CreateDateAfter(java.time.LocalDateTime time);
}