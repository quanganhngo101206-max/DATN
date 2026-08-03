package com.skysport.datn.service;

import com.skysport.datn.entity.Color;
import com.skysport.datn.repository.ColorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ColorService {
    @Autowired
    private ColorRepository colorRepository;

    public List<Color> findAll() { return colorRepository.findByDeleteFlag(false); }
    public void save(Color color) { color.setDeleteFlag(false); colorRepository.save(color); }
    public Color findById(Integer id) { return colorRepository.findById(id).orElse(null); }
    public void update(Color color) { colorRepository.save(color); }
    public void delete(Integer id) { Color c = findById(id); if(c != null) { c.setDeleteFlag(true); colorRepository.save(c); } }
}
