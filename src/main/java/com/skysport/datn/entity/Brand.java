package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Brand")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String code;

    private String name;

    private Integer status;

    private Boolean deleteFlag;
}