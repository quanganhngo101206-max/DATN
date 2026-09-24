package com.skysport.datn.service;

import com.skysport.datn.entity.Color;
import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.entity.Size;
import com.skysport.datn.repository.ColorRepository;
import com.skysport.datn.repository.ProductDetailRepository;
import com.skysport.datn.repository.SizeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import com.skysport.datn.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class ProductDetailService {

    private final ProductDetailRepository productDetailRepository;

    private final SizeRepository sizeRepository;

    private final ColorRepository colorRepository;

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
     *   thay vì tạo bản ghi mới, vì DB có ràng buộc UNIQUE(product_id, size_id, color_id).
     */
    @Transactional
    public void save(ProductDetail detail) {
        setDefaultValues(detail);
        validate(detail);

        Optional<ProductDetail> existing = productDetailRepository.findByProduct_IdAndSize_IdAndColor_Id(
                detail.getProduct().getId(), detail.getSize().getId(), detail.getColor().getId());

        if (existing.isPresent()) {
            ProductDetail found = existing.get();

            if (Boolean.FALSE.equals(found.getDeleteFlag())) {
                throw new BusinessException("Biến thể (Size: " + detail.getSize().getName()
                        + ", Màu: " + detail.getColor().getName()
                        + ") đã tồn tại. Vui lòng sửa biến thể có sẵn thay vì thêm mới.");
            }

            found.setQuantity(detail.getQuantity());
            found.setPrice(detail.getPrice());
            found.setBarcode(detail.getBarcode());
            found.setStatus(detail.getStatus());
            found.setMinStock(detail.getMinStock());
            found.setMaxStock(detail.getMaxStock());
            found.setTargetMarginPercent(detail.getTargetMarginPercent());
            found.setDeleteFlag(false);

            productDetailRepository.save(found);
            return;
        }

        try {
            detail.setDeleteFlag(false);
            productDetailRepository.save(detail);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("Không thể thêm biến thể: trùng dữ liệu (size/màu hoặc barcode đã tồn tại).");
        }
    }

    // Tìm theo id
    public ProductDetail findById(Integer id) {
        return productDetailRepository.findById(id).orElse(null);
    }

    // Sửa detail
    public void update(ProductDetail detail) {
        setDefaultValues(detail);
        validate(detail);
        productDetailRepository.save(detail);
    }

    private void validate(ProductDetail detail) {
        if (detail.getProduct() == null || detail.getProduct().getId() == null) {
            throw new BusinessException("Sản phẩm không hợp lệ");
        }

        if (detail.getSize() == null || detail.getColor() == null) {
            throw new BusinessException("Vui lòng chọn đầy đủ size và màu sắc");
        }

        if (detail.getQuantity() == null || detail.getQuantity() < 0) {
            throw new BusinessException("Số lượng không hợp lệ");
        }

        if (detail.getPrice() == null || detail.getPrice() <= 0) {
            throw new BusinessException("Giá bán phải lớn hơn 0");
        }

        if (detail.getMinStock() != null && detail.getMinStock() < 0) {
            throw new BusinessException("Tồn kho tối thiểu không được nhỏ hơn 0");
        }

        if (detail.getMaxStock() != null && detail.getMaxStock() < 0) {
            throw new BusinessException("Tồn kho tối đa không được nhỏ hơn 0");
        }

        if (detail.getMinStock() != null
                && detail.getMaxStock() != null
                && detail.getMaxStock() < detail.getMinStock()) {
            throw new BusinessException("Tồn kho tối đa phải lớn hơn hoặc bằng tồn kho tối thiểu");
        }

        if (detail.getStatus() != null
                && detail.getStatus() != 0
                && detail.getStatus() != 1) {
            throw new BusinessException("Trạng thái biến thể không hợp lệ");
        }

        if (detail.getTargetMarginPercent() != null
                && detail.getTargetMarginPercent() < 0) {
            throw new BusinessException("Tỷ lệ lợi nhuận mục tiêu không được nhỏ hơn 0");
        }
    }

    private void setDefaultValues(ProductDetail detail) {
        if (detail.getStatus() == null) {
            detail.setStatus(1);
        }

        if (detail.getMinStock() == null) {
            detail.setMinStock(10);
        }

        if (detail.getMaxStock() == null) {
            detail.setMaxStock(50);
        }
    }

    // Lấy size, color
    public List<Size> findAllSize() {
        return sizeRepository.findByDeleteFlag(false);
    }

    public List<Color> findAllColor() {
        return colorRepository.findByDeleteFlag(false);
    }

    // Chỉ lấy size/color đang Hoạt động — dùng cho dropdown khi thêm/sửa biến thể sản phẩm
    public List<Size> findAllActiveSize() {
        return sizeRepository.findByDeleteFlagFalseAndStatus(1);
    }

    public List<Color> findAllActiveColor() {
        return colorRepository.findByDeleteFlagAndStatus(false, 1).stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                .toList();
    }
}