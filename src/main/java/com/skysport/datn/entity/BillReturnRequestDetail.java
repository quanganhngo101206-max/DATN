package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Bill_return_request_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillReturnRequestDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Float momentPriceRefund;

    private Integer quantityReturn;

    @ManyToOne
    @JoinColumn(name = "return_id")
    private BillReturnRequest billReturnRequest;

    @ManyToOne
    @JoinColumn(name = "product_detail_id")
    private ProductDetail productDetail;
}