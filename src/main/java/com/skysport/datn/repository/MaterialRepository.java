package com.skysport.datn.repository;

import com.skysport.datn.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Integer> {
    List<Material> findByDeleteFlag(Boolean deleteFlag);
}
