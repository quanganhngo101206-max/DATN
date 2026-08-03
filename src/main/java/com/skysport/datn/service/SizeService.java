package com.skysport.datn.service;

import com.skysport.datn.entity.Size;
import com.skysport.datn.repository.SizeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SizeService {
    @Autowired
    private SizeRepository sizeRepository;

    public List<Size> findAll() { return sizeRepository.findByDeleteFlag(false); }
    public void save(Size size) { size.setDeleteFlag(false); sizeRepository.save(size); }
    public Size findById(Integer id) { return sizeRepository.findById(id).orElse(null); }
    public void update(Size size) { sizeRepository.save(size); }
    public void delete(Integer id) { Size s = findById(id); if(s != null) { s.setDeleteFlag(true); sizeRepository.save(s); } }
}
