package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * BillDetail — chi tiết hóa đơn.
 *
 * Phase 2 fix: bỏ @Data, dùng @Getter/@Setter/@EqualsAndHashCode/@ToString an toàn.
 *
 * @Data sinh toString()/equals()/hashCode() kéo theo toàn bộ field, bao gồm cả
 * relationship @ManyToOne — có thể gây StackOverflowError khi Bill.billDetails (LAZY)
 * bị trigger trong log/debug, hoặc khi Hibernate proxy serialize.
 *
 * Giải pháp:
 * - @EqualsAndHashCode(onlyExplicitlyIncluded = true): chỉ dùng id (stable, không thay đổi sau persist)
 * - @ToString(exclude = {"bill", "productDetail"}): tránh vòng lặp Bill → BillDetail → Bill
 */
@Entity
@Table(name = "Bill_detail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"bill", "productDetail"})
public class BillDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;

    private Float momentPrice;

    private Integer quantity;

    private Integer returnQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id")
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_detail_id")
    private ProductDetail productDetail;
}