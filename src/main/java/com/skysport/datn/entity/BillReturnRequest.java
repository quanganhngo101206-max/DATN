package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Bill_return_request")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String code;

    private LocalDateTime createdDate;

    private Integer status;

    @ManyToOne
    @JoinColumn(name = "bill_id")
    private Bill bill;
}