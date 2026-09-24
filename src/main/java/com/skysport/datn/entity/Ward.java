package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Wards")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"province"})
@NoArgsConstructor
@AllArgsConstructor
public class Ward {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;
    @Column(name = "name", nullable = false)
    private String name;
    @Column(name = "ward_code", length = 20)
    private String wardCode;           // mã 5 chữ số, chỉ để tra cứu
    @Column(name = "province_id")
    private Integer provinceId;        // FK trực tiếp → Provinces (bỏ districtId)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_id", insertable = false, updatable = false)
    private Province province;
}