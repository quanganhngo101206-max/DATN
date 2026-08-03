package com.skysport.datn.repository;

import com.skysport.datn.entity.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillReturnRequestRepository extends JpaRepository<ReturnRequest, Integer> {
    List<ReturnRequest> findAllByOrderByCreatedDateDesc();
    List<ReturnRequest> findByStatus(Integer status);
    List<ReturnRequest> findByBill_Id(Integer billId);
}