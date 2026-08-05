package com.skysport.datn.service;

import com.skysport.datn.entity.Color;
import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.entity.Size;
import com.skysport.datn.repository.ColorRepository;
import com.skysport.datn.repository.ProductDetailRepository;
import com.skysport.datn.repository.SizeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ProductDetailService {

    @Autowired
    private ProductDetailRepository productDetailRepository;

    @Autowired
    private SizeRepository sizeRepository;

    @Autowired
    private ColorRepository colorRepository;

    // Lấy detail theo product, sắp xếp theo Size rồi Color để trang admin
    // gom nhóm hiển thị theo từng size (mỗi size hiện đủ các màu bên trong)
    public List<ProductDetail> findByProduct(Integer productId) {
        List<ProductDetail> details = productDetailRepository.findByProductId(productId);
        details.sort(Comparator
                .comparing((ProductDetail d) -> d.getSize() != null ? d.getSize().getId() : Integer.MAX_VALUE)
                .thenComparing(d -> d.getColor() != null ? d.getColor().getId() : Integer.MAX_VALUE));
        return details;
    }

    /**
     * Thêm biến thể mới. Chặn trùng (product + size + color):
     * - Nếu đã có biến thể ACTIVE với cùng size/màu -> báo lỗi, không cho thêm.
     * - Nếu từng có biến thể với cùng size/màu nhưng đã bị xóa mềm -> khôi phục lại
     *   (cập nhật số lượng/giá/barcode) thay vì insert mới, vì DB có ràng buộc
     *   UNIQUE(product_id, size_id, color_id) áp dụng cho cả bản ghi đã xóa mềm.
     */
    @Transactional
    public void save(ProductDetail detail) {
        if (detail.getProduct() == null || detail.getProduct().getId() == null) {
            throw new RuntimeException("Sản phẩm không hợp lệ");
        }
        if (detail.getSize() == null || detail.getColor() == null) {
            throw new RuntimeException("Vui lòng chọn đầy đủ size và màu sắc");
        }
        if (detail.getQuantity() == null || detail.getQuantity() < 0) {
            throw new RuntimeException("Số lượng không hợp lệ");
        }
        if (detail.getPrice() == null || detail.getPrice() <= 0) {
            throw new RuntimeException("Giá bán phải lớn hơn 0");
        }

        Optional<ProductDetail> existing = productDetailRepository.findByProduct_IdAndSize_IdAndColor_Id(
                detail.getProduct().getId(), detail.getSize().getId(), detail.getColor().getId());

        if (existing.isPresent()) {
            ProductDetail found = existing.get();
            if (Boolean.FALSE.equals(found.getDeleteFlag())) {
                throw new RuntimeException("Biến thể (Size: " + detail.getSize().getName()
                        + ", Màu: " + detail.getColor().getName()
                        + ") đã tồn tại. Vui lòng sửa biến thể có sẵn thay vì thêm mới.");
            }
            // Khôi phục biến thể đã xóa mềm thay vì tạo bản ghi mới
            found.setQuantity(detail.getQuantity());
            found.setPrice(detail.getPrice());
            found.setBarcode(detail.getBarcode());
            found.setDeleteFlag(false);
            productDetailRepository.save(found);
            return;
        }

        try {
            detail.setDeleteFlag(false);
            productDetailRepository.save(detail);
        } catch (DataIntegrityViolationException e) {
            // Lưới an toàn cho race-condition hoặc trùng barcode
            throw new RuntimeException("Không thể thêm biến thể: trùng dữ liệu (size/màu hoặc barcode đã tồn tại).");
        }
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