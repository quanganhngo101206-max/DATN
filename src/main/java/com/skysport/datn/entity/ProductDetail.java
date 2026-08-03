package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;

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
}
