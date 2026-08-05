package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Product")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String code;
    private String name;
    private LocalDateTime createDate;

    @Column(name = "update_date")
    private LocalDateTime updatedDate;

    private Integer status;
    private Boolean deleteFlag;
    private Integer gender;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String describe;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @ManyToOne
    @JoinColumn(name = "material_id")
    private Material material;
}
