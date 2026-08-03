package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Account")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private LocalDateTime birthDay;

    private String code;

    private LocalDateTime createDate;

    private String email;

    private Boolean isNonLocked;

    private Integer failedAttempts;

    private LocalDateTime updateDate;

    private String username;

    @Column(name = "pass_word")
    private String password;

    private Integer status;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;
}