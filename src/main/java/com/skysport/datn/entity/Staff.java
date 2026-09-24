package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Staff")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"account"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;

    private String code;
    private String name;
    private Integer status;

    private String phoneNumber;
    private String email;
    private Boolean gender;
    private String address;

    @ManyToOne
    @JoinColumn(name = "account_id")
    private Account account;
}