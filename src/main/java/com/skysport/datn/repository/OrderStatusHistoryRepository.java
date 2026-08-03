package com.skysport.datn.repository;

import com.skysport.datn.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Integer> {

    List<OrderStatusHistory> findByBillIdOrderByCreatedDateAsc(Integer billId);
}