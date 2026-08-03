package com.skysport.datn.repository;

import com.skysport.datn.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Integer> {
    List<Supplier> findByStatusAndDeleteFlagFalse(Integer status);
}
