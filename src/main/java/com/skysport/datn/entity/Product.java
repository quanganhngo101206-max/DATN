package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Product")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"category", "brand", "material"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
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

    // Category/Brand/Material của sản phẩm còn "Hoạt động" hay đã bị "Tạm dừng"
    public boolean isCategoryActive() {
        return category != null && category.getStatus() != null && category.getStatus() == 1;
    }

    public boolean isBrandActive() {
        return brand != null && brand.getStatus() != null && brand.getStatus() == 1;
    }

    public boolean isMaterialActive() {
        return material != null && material.getStatus() != null && material.getStatus() == 1;
    }

    // true nếu cả 3 danh mục gốc (category/brand/material) đều đang Hoạt động.
    // Dùng để: (1) ẩn sản phẩm khỏi trang khách hàng, (2) hiện badge cảnh báo ở admin/staff.
    public boolean isMasterDataActive() {
        return isCategoryActive() && isBrandActive() && isMaterialActive();
    }
}