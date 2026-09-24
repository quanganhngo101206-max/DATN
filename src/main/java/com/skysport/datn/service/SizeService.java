package com.skysport.datn.service;

import com.skysport.datn.entity.Size;
import com.skysport.datn.repository.SizeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SizeService {
    private final SizeRepository sizeRepository;

    public List<Size> findAll() {
        return sizeRepository.findByDeleteFlag(false);
    }

    public void save(Size size) {
        size.setDeleteFlag(false);
        size.setStatus(0);
        sizeRepository.save(size);
    }

    public Size findById(Integer id) {
        return sizeRepository.findById(id).orElse(null);
    }

    public void update(Size size) {
        sizeRepository.save(size);
    }

    public void delete(Integer id) {
        Size s = findById(id);
        if (s != null) {
            s.setDeleteFlag(true);
            sizeRepository.save(s);
        }
    }

    public void toggleStatus(Integer id) {
        Size size = findById(id);
        if (size != null) {
            size.setStatus(
                    size.getStatus() != null && size.getStatus() == 1 ? 0 : 1);
            sizeRepository.save(size);
        }
    }

    public List<Size> findActiveSizes() {
        return sizeRepository.findByDeleteFlagFalseAndStatus(1);
    }
}