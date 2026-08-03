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

    // Biến thể có đang trong thời gian khuyến mãi hiệu lực hay không
    public boolean isOnSale() {
        if (productDiscount == null || Boolean.TRUE.equals(productDiscount.getClosed())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = productDiscount.getStartDate();
        LocalDateTime end = productDiscount.getEndDate();
        return start != null && end != null && !start.isAfter(now) && !end.isBefore(now);
    }

    // Giá sau khi trừ khuyến mãi (nếu đang sale), ngược lại trả về giá gốc
    public Float getFinalPrice() {
        if (isOnSale() && price != null && productDiscount.getDiscountedAmount() != null) {
            float finalPrice = price - productDiscount.getDiscountedAmount();
            return Math.max(finalPrice, 0f);
        }
        return price;
    }
}