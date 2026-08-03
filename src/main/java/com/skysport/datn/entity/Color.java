package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Color")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Color {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String code;

    private String name;

    private Boolean deleteFlag;
}