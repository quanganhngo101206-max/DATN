package com.skysport.datn.service;

import com.skysport.datn.entity.Color;
import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.entity.Size;
import com.skysport.datn.repository.ColorRepository;
import com.skysport.datn.repository.ProductDetailRepository;
import com.skysport.datn.repository.SizeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductDetailService {

    @Autowired
    private ProductDetailRepository productDetailRepository;

    @Autowired
    private SizeRepository sizeRepository;

    @Autowired
    private ColorRepository colorRepository;

    // Lấy detail theo product
    public List<ProductDetail> findByProduct(Integer productId) {
        return productDetailRepository.findByProductId(productId);
    }

    // Thêm detail
    public void save(ProductDetail detail) {
        detail.setDeleteFlag(false);
        productDetailRepository.save(detail);
    }

    // Tìm theo id
    public ProductDetail findById(Integer id) {
        return productDetailRepository.findById(id).orElse(null);
    }

    // Sửa detail
    public void update(ProductDetail detail) {
        productDetailRepository.save(detail);
    }

    // Xóa detail
    public void delete(Integer id) {
        ProductDetail d = findById(id);
        if (d != null) {
            d.setDeleteFlag(true);
            productDetailRepository.save(d);
        }
    }

    // Lấy size, color
    public List<Size> findAllSize() {
        return sizeRepository.findByDeleteFlag(false);
    }

    public List<Color> findAllColor() {
        return colorRepository.findByDeleteFlag(false);
    }
}
