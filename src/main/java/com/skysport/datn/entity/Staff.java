package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Staff")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String code;
    private String name;
    private Integer status;

    private String phoneNumber;   // ✅ Thêm mới
    private String email;         // ✅ Thêm mới
    private Boolean gender;       // ✅ Thêm mới
    private String address;       // ✅ Thêm mới

    @ManyToOne
    @JoinColumn(name = "account_id")
    private Account account;
}