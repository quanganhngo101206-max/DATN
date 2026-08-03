package com.skysport.datn.repository;

import com.skysport.datn.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {
    List<Product> findByDeleteFlag(Boolean deleteFlag);
    List<Product> findByDeleteFlagFalse();
}