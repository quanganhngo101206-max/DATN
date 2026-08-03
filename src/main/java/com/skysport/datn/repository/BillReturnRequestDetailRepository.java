package com.skysport.datn.repository;

import com.skysport.datn.entity.ReturnRequestDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillReturnRequestDetailRepository extends JpaRepository<ReturnRequestDetail, Integer> {
    List<ReturnRequestDetail> findByBillReturnRequest_Id(Integer requestId);
}
