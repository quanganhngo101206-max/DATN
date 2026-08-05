package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Address_shipping")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressShipping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String address;

    private String receiverName;
    private String receiverPhone;
    private Boolean isDefault;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    // ✅ Bỏ insertable=false, updatable=false
    // để JPA có thể ghi/cập nhật các cột này
    @Column(name = "province_id")
    private Integer provinceId;


    @Column(name = "ward_id")
    private Integer wardId;
}