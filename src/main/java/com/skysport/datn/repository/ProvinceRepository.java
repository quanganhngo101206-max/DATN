package com.skysport.datn.repository;

import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.Province;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProvinceRepository extends JpaRepository<Province, Integer> {
}