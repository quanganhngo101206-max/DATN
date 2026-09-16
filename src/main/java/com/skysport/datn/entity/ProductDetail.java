package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Product_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer quantity;

    private Float price;

    private String barcode;

    private Boolean deleteFlag;

    // =========================
    // QUẢN LÝ BIẾN THỂ
    // =========================

    /**
     * Trạng thái trực tiếp của biến thể:
     * 1 = Hoạt động
     * 0 = Tạm dừng
     */
    @Column(name = "status")
    private Integer status;

    /**
     * Tồn kho tối thiểu.
     * Khi quantity < minStock -> cảnh báo sắp hết hàng.
     */
    @Column(name = "min_stock")
    private Integer minStock;

    /**
     * Tồn kho tối đa.
     * Khi quantity > maxStock -> cảnh báo tồn kho cao.
     */
    @Column(name = "max_stock")
    private Integer maxStock;

    /**
     * Tỷ lệ lợi nhuận mục tiêu (%).
     *
     * Ví dụ:
     * importPrice = 200.000
     * targetMarginPercent = 30
     * -> giá bán gợi ý = 260.000
     */
    @Column(name = "target_margin_percent")
    private Float targetMarginPercent;

    // =========================
    // QUAN HỆ
    // =========================

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne
    @JoinColumn(name = "size_id")
    private Size size;

    @ManyToOne
    @JoinColumn(name = "color_id")
    private Color color;

    @ManyToOne
    @JoinColumn(name = "product_discount_id")
    private ProductDiscount productDiscount;

    // =========================
    // KHUYẾN MÃI SẢN PHẨM
    // =========================

    /**
     * Kiểm tra biến thể hiện có đang được giảm giá hay không.
     */
    public boolean isOnSale() {
        if (productDiscount == null
                || Boolean.TRUE.equals(productDiscount.getClosed())) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime start = productDiscount.getStartDate();
        LocalDateTime end = productDiscount.getEndDate();

        return start != null
                && end != null
                && !start.isAfter(now)
                && !end.isBefore(now);
    }

    /**
     * Lấy giá bán cuối cùng sau khi áp dụng giảm giá.
     */
    public Float getFinalPrice() {
        if (isOnSale()
                && price != null
                && productDiscount.getDiscountedAmount() != null) {

            float finalPrice =
                    price - productDiscount.getDiscountedAmount();

            return Math.max(finalPrice, 0f);
        }

        return price;
    }

    // =========================
    // TRẠNG THÁI SIZE / COLOR
    // =========================

    /**
     * Kiểm tra Size có đang hoạt động hay không.
     *
     * Size:
     * 1 = Hoạt động
     * 0 = Tạm dừng
     */
    public boolean isSizeActive() {
        return size != null
                && size.getStatus() != null
                && size.getStatus() == 1;
    }

    /**
     * Kiểm tra Color có đang hoạt động hay không.
     *
     * Color:
     * 1 = Hoạt động
     * 0 = Tạm dừng
     */
    public boolean isColorActive() {
        return color != null
                && color.getStatus() != null
                && color.getStatus() == 1;
    }

    // =========================
    // TRẠNG THÁI BIẾN THỂ
    // =========================

    /**
     * Kiểm tra trạng thái trực tiếp của biến thể.
     *
     * 1 = Hoạt động
     * 0 = Tạm dừng
     */
    public boolean isVariantStatusActive() {
        return status != null && status == 1;
    }

    /**
     * Kiểm tra biến thể có tồn kho hay không.
     *
     * quantity > 0 = còn hàng
     * quantity <= 0 = hết hàng
     */
    public boolean isStockAvailable() {
        return quantity != null && quantity > 0;
    }

    /**
     * Kiểm tra Product có đang hoạt động hay không.
     *
     * Product:
     * 1 = Hoạt động
     * 0 = Không hoạt động
     */
    public boolean isProductActive() {
        return product != null
                && product.getStatus() != null
                && product.getStatus() == 1;
    }

    /**
     * Kiểm tra biến thể có đang hoạt động dựa trên:
     * - Trạng thái biến thể
     * - Size
     * - Color
     */
    public boolean isVariantActive() {
        return isVariantStatusActive()
                && isSizeActive()
                && isColorActive();
    }

    /**
     * Kiểm tra biến thể có thực sự được phép bán hay không.
     *
     * Điều kiện:
     * 1. Product đang hoạt động
     * 2. Biến thể đang hoạt động
     * 3. Size đang hoạt động
     * 4. Color đang hoạt động
     * 5. Còn hàng
     */
    public boolean isSellable() {
        return isProductActive()
                && isVariantStatusActive()
                && isSizeActive()
                && isColorActive()
                && isStockAvailable();
    }
}