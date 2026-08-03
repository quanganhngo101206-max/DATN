package com.skysport.datn.service;

import com.skysport.datn.entity.Category;
import com.skysport.datn.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    // Lấy tất cả danh mục chưa xóa
    public List<Category> findAll() {
        return categoryRepository.findByDeleteFlag(false);
    }

    // Thêm danh mục
    public void save(Category category) {
        category.setDeleteFlag(false);
        category.setStatus(1);
        categoryRepository.save(category);
    }

    // Tìm theo id
    public Category findById(Integer id) {
        return categoryRepository.findById(id).orElse(null);
    }

    // Sửa danh mục
    public void update(Category category) {
        categoryRepository.save(category);
    }

    // Xóa mềm
    public void delete(Integer id) {
        Category category = findById(id);
        if (category != null) {
            category.setDeleteFlag(true);
            categoryRepository.save(category);
        }
    }
}