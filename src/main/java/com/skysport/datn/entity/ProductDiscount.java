package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Product_discount")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDiscount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "[close]")
    private Boolean closed;

    @Column(name = "discount_amount")
    private Float discountedAmount;

    private LocalDateTime endDate;

    private LocalDateTime startDate;

    @ManyToOne
    @JoinColumn(name = "product_detail_id")
    private ProductDetail productDetail;
}
