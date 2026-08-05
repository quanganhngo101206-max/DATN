package com.skysport.datn.repository;

import com.skysport.datn.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentMethodRepository extends JpaRepository<Payment, Integer> {
}