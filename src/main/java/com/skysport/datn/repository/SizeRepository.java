package com.skysport.datn.repository;

import com.skysport.datn.entity.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SizeRepository extends JpaRepository<Size, Integer> {
    List<Size> findByDeleteFlag(Boolean deleteFlag);
}
