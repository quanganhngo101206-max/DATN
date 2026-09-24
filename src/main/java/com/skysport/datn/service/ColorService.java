package com.skysport.datn.service;

import com.skysport.datn.entity.Color;
import com.skysport.datn.repository.ColorRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ColorService {
    private final ColorRepository colorRepository;

    public List<Color> findAll() {
        return colorRepository.findByDeleteFlag(false);
    }

    public void save(Color color) {
        color.setDeleteFlag(false);
        color.setStatus(0);
        colorRepository.save(color);
    }

    public Color findById(Integer id) {
        return colorRepository.findById(id).orElse(null);
    }

    public void update(Color color) {
        colorRepository.save(color);
    }

    public void delete(Integer id) {
        Color c = findById(id);
        if (c != null) {
            c.setDeleteFlag(true);
            colorRepository.save(c);
        }
    }

    public void toggleStatus(Integer id) {
        Color color = findById(id);
        if (color != null) {
            color.setStatus(
                    color.getStatus() != null && color.getStatus() == 1 ? 0 : 1);
            colorRepository.save(color);
        }
    }
}