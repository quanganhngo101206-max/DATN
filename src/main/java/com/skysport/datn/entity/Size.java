package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Size")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Size {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String code;

    private String name;

    private Boolean deleteFlag;

    // 1 = Hoạt động, 0 = Tạm dừng. Mặc định khi tạo mới là 0 (Tạm dừng).
    private Integer status;
}