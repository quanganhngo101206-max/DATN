package com.skysport.datn.service;

import com.skysport.datn.entity.Brand;
import com.skysport.datn.entity.Category;
import com.skysport.datn.entity.Material;
import com.skysport.datn.entity.Product;
import com.skysport.datn.repository.BrandRepository;
import com.skysport.datn.repository.CategoryRepository;
import com.skysport.datn.repository.MaterialRepository;
import com.skysport.datn.repository.ProductDetailRepository;
import com.skysport.datn.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Autowired
    private ProductDetailRepository productDetailRepository;

    // Lấy tất cả sản phẩm
    public List<Product> findAll() {
        return productRepository.findByDeleteFlag(false);
    }

    // Tìm kiếm + lọc (danh mục, thương hiệu, chất liệu, size, màu, trạng thái) + phân trang (dùng cho trang danh sách admin)
    public Page<Product> search(String keyword, Integer categoryId, Integer brandId, Integer materialId,
                                Integer sizeId, Integer colorId, Integer status, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return productRepository.search(kw, categoryId, brandId, materialId, sizeId, colorId, status, pageable);
    }

    // Tổng tồn kho của từng sản phẩm trong danh sách id truyền vào, để hiển thị ngay ở bảng danh sách
    public Map<Integer, Integer> getQuantityMap(List<Integer> productIds) {
        Map<Integer, Integer> result = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return result;
        }
        for (Object[] row : productDetailRepository.sumQuantityByProductIds(productIds)) {
            Integer productId = (Integer) row[0];
            Long total = ((Number) row[1]).longValue();
            result.put(productId, total.intValue());
        }
        return result;
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