package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Bill_return_request_detail")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"billReturnRequest", "productDetail"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillReturnRequestDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
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