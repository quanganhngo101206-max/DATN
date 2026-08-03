package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Order_status_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer status;

    private String note;

    private LocalDateTime createdDate;

    @ManyToOne
    @JoinColumn(name = "bill_id")
    private Bill bill;

    @ManyToOne
    @JoinColumn(name = "staff_id")
    private Staff staff;
}