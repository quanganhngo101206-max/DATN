package com.skysport.datn.service;

import com.skysport.datn.entity.Brand;
import com.skysport.datn.repository.BrandRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BrandService {
    @Autowired
    private BrandRepository brandRepository;

    public List<Brand> findAll() { return brandRepository.findByDeleteFlag(false); }
    public void save(Brand brand) { brand.setDeleteFlag(false); brand.setStatus(1); brandRepository.save(brand); }
    public Brand findById(Integer id) { return brandRepository.findById(id).orElse(null); }
    public void update(Brand brand) { brandRepository.save(brand); }
    public void delete(Integer id) { Brand b = findById(id); if(b != null) { b.setDeleteFlag(true); brandRepository.save(b); } }
}
