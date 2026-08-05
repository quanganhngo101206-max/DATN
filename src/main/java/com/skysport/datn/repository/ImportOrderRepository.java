package com.skysport.datn.repository;

import com.skysport.datn.entity.ImportOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportOrderRepository extends JpaRepository<ImportOrder, Integer> {
    List<ImportOrder> findByStaffIdOrderByCreateDateDesc(Integer staffId);
    List<ImportOrder> findAllByOrderByCreateDateDesc();
    List<ImportOrder> findByStatus(Integer status);
}