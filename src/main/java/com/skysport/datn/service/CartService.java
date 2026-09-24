package com.skysport.datn.service;

import com.skysport.datn.entity.Image;
import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.repository.ImageRepository;
import com.skysport.datn.repository.ProductDetailRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartService {

    // Giới hạn số lượng tối đa cho 1 biến thể (size/màu) trong giỏ hàng,
    // bất kể tồn kho còn bao nhiêu.
    public static final int MAX_QUANTITY_PER_ORDER = 15;

    private final ProductDetailRepository productDetailRepository;

    private final ImageRepository imageRepository;

    // Tìm ProductDetail theo productId, color, size
    public ProductDetail findProductDetail(Integer productId, String color, String size) {
        List<ProductDetail> details =
                productDetailRepository.findByProductIdAndDeleteFlagFalse(productId);

        return details.stream()
                .filter(d -> {
                    // Không cho thao tác với sản phẩm đã bị xóa mềm
                    var p = d.getProduct();

                    if (p == null
                            || Boolean.TRUE.equals(p.getDeleteFlag())
                            || p.getStatus() == null
                            || p.getStatus() != 1) {
                        return false;
                    }

                    // Biến thể phải đang hoạt động
                    if (!d.isVariantStatusActive()) {
                        return false;
                    }

                    // Size phải đang hoạt động
                    if (!d.isSizeActive()) {
                        return false;
                    }

                    // Color phải đang hoạt động
                    if (!d.isColorActive()) {
                        return false;
                    }

                    boolean colorMatch =
                            (color == null || color.isEmpty())
                                    || (d.getColor() != null
                                    && color.equalsIgnoreCase(d.getColor().getName()));

                    boolean sizeMatch =
                            (size == null || size.isEmpty())
                                    || (d.getSize() != null
                                    && size.equalsIgnoreCase(d.getSize().getName()));

                    return colorMatch && sizeMatch;
                })
                .findFirst()
                .orElse(null);
    }

    // Kiểm tra tồn kho và trạng thái có thể bán
    // (số lượng đặt phải > 0, không vượt quá MAX_QUANTITY_PER_ORDER,
    // và không vượt quá tồn kho thực tế)
    public boolean checkStock(ProductDetail detail, int requestedQty) {
        return detail != null
                && requestedQty > 0
                && requestedQty <= MAX_QUANTITY_PER_ORDER
                && detail.isSellable()
                && detail.getQuantity() != null
                && detail.getQuantity() >= requestedQty;
    }

    // Lấy ảnh sản phẩm
    public String getProductImage(Integer productId) {
        List<Image> images = imageRepository.findByProductId(productId);
        return images.isEmpty() ? null : images.get(0).getLink();
    }

    // Lấy ProductDetail theo id
    public ProductDetail getProductDetailById(Integer detailId) {
        return productDetailRepository.findById(detailId).orElse(null);
    }

    public void updateProductDetail(ProductDetail detail) {
        productDetailRepository.save(detail);
    }
}