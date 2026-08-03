package com.skysport.datn.repository;

import com.skysport.datn.entity.Color;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ColorRepository extends JpaRepository<Color, Integer> {
    List<Color> findByDeleteFlag(Boolean deleteFlag);
}