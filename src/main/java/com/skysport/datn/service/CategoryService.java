package com.skysport.datn.service;

import com.skysport.datn.entity.Category;
import com.skysport.datn.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    // Lấy tất cả danh mục (Hoạt động + Tạm dừng) — dùng cho trang quản lý
    public List<Category> findAll() {
        return categoryRepository.findByDeleteFlag(false);
    }

    // Lấy các danh mục đang Hoạt động — dùng khi chọn category cho sản phẩm (admin/staff)
    public List<Category> findAllActive() {
        return categoryRepository.findByDeleteFlag(false).stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                .collect(Collectors.toList());
    }

    // Thêm danh mục — mặc định Tạm dừng (0), admin phải chủ động Bật lên
    public void save(Category category) {
        category.setDeleteFlag(false);
        category.setStatus(0);
        categoryRepository.save(category);
    }

    // Tìm theo id
    public Category findById(Integer id) {
        return categoryRepository.findById(id).orElse(null);
    }

    // Sửa danh mục (không đổi status qua form sửa — status chỉ đổi qua nút Bật/Tắt)
    public void update(Category category) {
        Category old = findById(category.getId());
        if (old == null) return;
        old.setCode(category.getCode());
        old.setName(category.getName());
        categoryRepository.save(old);
    }

    // Bật/Tắt hoạt động — thay thế hoàn toàn cho chức năng xóa
    public void toggleStatus(Integer id) {
        Category category = findById(id);
        if (category != null) {
            int current = category.getStatus() != null ? category.getStatus() : 0;
            category.setStatus(current == 1 ? 0 : 1);
            categoryRepository.save(category);
        }
    }
}