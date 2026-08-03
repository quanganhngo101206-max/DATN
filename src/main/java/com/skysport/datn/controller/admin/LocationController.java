package com.skysport.datn.controller.admin;

import com.skysport.datn.entity.Province;
import com.skysport.datn.entity.Ward;
import com.skysport.datn.repository.ProvinceRepository;
import com.skysport.datn.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final ProvinceRepository provinceRepository;
    private final WardRepository wardRepository;

    @GetMapping("/provinces")
    public List<Province> getProvinces() {
        return provinceRepository.findAll();
    }

    // Đã bỏ endpoint /districts vì Ward map thẳng lên Province

    @GetMapping("/wards")
    public List<Ward> getWards(@RequestParam Integer provinceId) {
        return wardRepository.findByProvinceIdOrderByNameAsc(provinceId);
    }
}
