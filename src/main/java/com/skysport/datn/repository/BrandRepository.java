package com.skysport.datn.repository;

import com.skysport.datn.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Integer> {
    List<Brand> findByDeleteFlag(Boolean deleteFlag);
}