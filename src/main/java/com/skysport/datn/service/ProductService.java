package com.skysport.datn.service;

import com.skysport.datn.entity.Brand;
import com.skysport.datn.entity.Category;
import com.skysport.datn.entity.Material;
import com.skysport.datn.entity.Product;
import com.skysport.datn.repository.BrandRepository;
import com.skysport.datn.repository.CategoryRepository;
import com.skysport.datn.repository.MaterialRepository;
import com.skysport.datn.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private MaterialRepository materialRepository;

    // Lấy tất cả sản phẩm
    public List<Product> findAll() {
        return productRepository.findByDeleteFlag(false);
    }

    // Tìm theo id
    public Product findById(Integer id) {
        return productRepository.findById(id).orElse(null);
    }

    // Thêm sản phẩm
    public void save(Product product) {
        product.setDeleteFlag(false);
        product.setStatus(1);
        product.setCreateDate(LocalDateTime.now());
        product.setUpdatedDate(LocalDateTime.now());
        // ❌ Đã xóa dòng setPrice
        productRepository.save(product);
    }

    // Sửa sản phẩm
    public void update(Product product) {
        product.setUpdatedDate(LocalDateTime.now());
        productRepository.save(product);
    }

    // Xóa mềm
    public void delete(Integer id) {
        Product p = findById(id);
        if (p != null) {
            p.setDeleteFlag(true);
            productRepository.save(p);
        }
    }

    // Lấy danh mục, thương hiệu, chất liệu
    public List<Category> findAllCategory() {
        return categoryRepository.findByDeleteFlag(false);
    }

    public List<Brand> findAllBrand() {
        return brandRepository.findByDeleteFlag(false);
    }

    public List<Material> findAllMaterial() {
        return materialRepository.findByDeleteFlag(false);
    }
}