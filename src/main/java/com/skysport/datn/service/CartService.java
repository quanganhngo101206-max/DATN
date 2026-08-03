package com.skysport.datn.service;

import com.skysport.datn.entity.Image;
import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.repository.ImageRepository;
import com.skysport.datn.repository.ProductDetailRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CartService {

    @Autowired
    private ProductDetailRepository productDetailRepository;

    @Autowired
    private ImageRepository imageRepository;

    // Tìm ProductDetail theo productId, color, size
    public ProductDetail findProductDetail(Integer productId, String color, String size) {
        List<ProductDetail> details = productDetailRepository.findByProductIdAndDeleteFlagFalse(productId);

        return details.stream()
                .filter(d -> {
                    // Không cho thao tác với sản phẩm đã bị xóa mềm hoặc đang ẩn
                    var p = d.getProduct();
                    if (p == null || Boolean.TRUE.equals(p.getDeleteFlag())
                            || p.getStatus() == null || p.getStatus() != 1) {
                        return false;
                    }
                    boolean colorMatch = (color == null || color.isEmpty()) ||
                            (d.getColor() != null && color.equalsIgnoreCase(d.getColor().getName()));
                    boolean sizeMatch = (size == null || size.isEmpty()) ||
                            (d.getSize() != null && size.equalsIgnoreCase(d.getSize().getName()));
                    return colorMatch && sizeMatch;
                })
                .findFirst()
                .orElse(null);
    }

    // Kiểm tra tồn kho
    public boolean checkStock(ProductDetail detail, int requestedQty) {
        return detail != null && detail.getQuantity() != null && detail.getQuantity() >= requestedQty;
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