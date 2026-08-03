package com.skysport.datn.repository;

import com.skysport.datn.entity.BillReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillReturnRepository extends JpaRepository<BillReturn, Integer> {
}
