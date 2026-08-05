package com.skysport.datn.repository;

import com.skysport.datn.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

// StaffRepository
@Repository
public interface StaffRepository extends JpaRepository<Staff, Integer> {
    List<Staff> findAll();
    @Query("SELECT s FROM Staff s WHERE s.account.id = :accountId")
    Staff findByAccountId(@Param("accountId") Integer accountId);}

