package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Account")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"role"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;

    private LocalDateTime birthDay;

    private String code;

    private LocalDateTime createDate;

    private String email;

    @Builder.Default
    @Column(name = "is_non_locked", nullable = false)
    private Boolean isNonLocked = true;

    @Builder.Default
    @Column(name = "failed_attempts", nullable = false)
    private Integer failedAttempts = 0;

    private LocalDateTime updateDate;

    private String username;

    @Column(name = "pass_word")
    private String password;

    private Integer status;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;
}