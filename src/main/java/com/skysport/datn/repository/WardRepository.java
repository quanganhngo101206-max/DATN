package com.skysport.datn.repository;

import com.skysport.datn.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WardRepository extends JpaRepository<Ward, Integer> {
    List<Ward> findByProvinceIdOrderByNameAsc(Integer provinceId);
}