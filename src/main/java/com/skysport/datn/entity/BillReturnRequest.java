package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Bill_return_request")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"bill"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;

    private String code;

    private LocalDateTime createdDate;

    private Integer status;

    @ManyToOne
    @JoinColumn(name = "bill_id")
    private Bill bill;
}