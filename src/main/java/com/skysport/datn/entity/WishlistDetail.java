package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Wishlist_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "wishlist_id")
    private Wishlist wishlist;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;
}