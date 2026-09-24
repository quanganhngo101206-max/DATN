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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final MaterialRepository materialRepository;
    private final ProductDetailRepository productDetailRepository;

    // ===== DTO cho chatbot — tránh N+1 =====

    /**
     * DTO nhẹ dùng cho chatbot: [id, name, image, price].
     * Được build từ Object[] trả về bởi productRepository.searchForChatbot().
     */
    @Getter
    public static class ProductChatbotDto {
        private final Integer id;
        private final String  name;
        private final String  image;
        private final Float   price;

        public ProductChatbotDto(Object[] row) {
            this.id    = row[0] != null ? ((Number) row[0]).intValue() : null;
            this.name  = row[1] != null ? (String) row[1] : null;
            this.image = row[2] != null ? (String) row[2] : null;
            this.price = row[3] != null ? ((Number) row[3]).floatValue() : null;
        }
    }

    /**
     * Tìm sản phẩm cho chatbot — 1 query duy nhất, không N+1.
     * ProductRepository.searchForChatbot() JOIN Image + ProductDetail trong SQL,
     * trả về [id, name, firstImage, minPrice].
     */
    public List<ProductChatbotDto> searchForChatbot(String keyword, int limit) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        List<Object[]> rows = productRepository.searchForChatbot(kw, PageRequest.of(0, limit));
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        return rows.stream().map(ProductChatbotDto::new).toList();
    }

    // Lấy tất cả sản phẩm
    public List<Product> findAll() {
        return productRepository.findByDeleteFlag(false);
    }

    // Tìm kiếm + lọc (danh mục, thương hiệu, chất liệu, size, màu, trạng thái) + phân trang
    public Page<Product> search(String keyword, Integer categoryId, Integer brandId, Integer materialId,
                                Integer sizeId, Integer colorId, Integer status, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return productRepository.search(kw, categoryId, brandId, materialId, sizeId, colorId, status, pageable);
    }

    // Tổng tồn kho của từng sản phẩm trong danh sách id
    public Map<Integer, Integer> getQuantityMap(List<Integer> productIds) {
        Map<Integer, Integer> result = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) return result;
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
        productRepository.save(product);
    }

    // Sửa sản phẩm
    public void update(Product product) {
        product.setUpdatedDate(LocalDateTime.now());
        productRepository.save(product);
    }

    // Lấy danh mục, thương hiệu, chất liệu
    public List<Category> findAllCategory()  { return categoryRepository.findByDeleteFlag(false); }
    public List<Brand>    findAllBrand()     { return brandRepository.findByDeleteFlag(false); }
    public List<Material> findAllMaterial()  { return materialRepository.findByDeleteFlag(false); }

    // Chỉ lấy các mục đang Hoạt động — dùng cho dropdown thêm/sửa sản phẩm
    public List<Category> findAllActiveCategory() {
        return categoryRepository.findByDeleteFlag(false).stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                .toList();
    }

    public List<Brand> findAllActiveBrand() {
        return brandRepository.findByDeleteFlag(false).stream()
                .filter(b -> b.getStatus() != null && b.getStatus() == 1)
                .toList();
    }

    public List<Material> findAllActiveMaterial() {
        return materialRepository.findByDeleteFlag(false).stream()
                .filter(m -> m.getStatus() != null && m.getStatus() == 1)
                .toList();
    }

    // Bật / tắt trạng thái sản phẩm
    public void toggleStatus(Integer id) {
        Product product = findById(id);
        if (product != null) {
            product.setStatus(product.getStatus() != null && product.getStatus() == 1 ? 0 : 1);
            product.setUpdatedDate(LocalDateTime.now());
            productRepository.save(product);
        }
    }
}