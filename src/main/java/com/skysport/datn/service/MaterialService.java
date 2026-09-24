package com.skysport.datn.service;

import com.skysport.datn.entity.Material;
import com.skysport.datn.repository.MaterialRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MaterialService {
    private final MaterialRepository materialRepository;

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

    public void toggleStatus(Integer id) {
        Material material = findById(id);

        if (material != null) {
            material.setStatus(
                    material.getStatus() != null && material.getStatus() == 1 ? 0 : 1);
            materialRepository.save(material);
        }
    }
}