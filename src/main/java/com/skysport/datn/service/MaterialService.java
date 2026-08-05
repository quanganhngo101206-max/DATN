package com.skysport.datn.service;

import com.skysport.datn.entity.Material;
import com.skysport.datn.repository.MaterialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MaterialService {
    @Autowired
    private MaterialRepository materialRepository;

    public List<Material> findAll() {
        return materialRepository.findByDeleteFlag(false);
    }

    public void save(Material material) {
        material.setDeleteFlag(false);
        material.setStatus(1);
        materialRepository.save(material);
    }

    public Material findById(Integer id) {
        return materialRepository.findById(id).orElse(null);
    }

    public void update(Material material) {
        materialRepository.save(material);
    }

    public void delete(Integer id) {
        Material m = findById(id);
        if (m != null) {
            m.setDeleteFlag(true);
            materialRepository.save(m);
        }
    }
}
