package com.skysport.datn.service;

import com.skysport.datn.entity.Province;
import com.skysport.datn.entity.Ward;
import com.skysport.datn.repository.ProvinceRepository;
import com.skysport.datn.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final ProvinceRepository provinceRepo;
    private final WardRepository wardRepo;

    public List<Province> getAllProvinces() {
        return provinceRepo.findAll();
    }

    // ĐÃ SỬA: nhận provinceId thay vì districtId
    public List<Ward> getWardsByProvince(Integer provinceId) {
        return wardRepo.findByProvinceIdOrderByNameAsc(provinceId);
    }
}
