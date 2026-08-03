package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Import_order_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportOrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer quantity;

    private Float importPrice;

    @ManyToOne
    @JoinColumn(name = "import_order_id")
    private ImportOrder importOrder;

    @ManyToOne
    @JoinColumn(name = "product_detail_id")
    private ProductDetail productDetail;
}