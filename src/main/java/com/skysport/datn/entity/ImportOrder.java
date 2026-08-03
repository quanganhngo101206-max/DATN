package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Import_order")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String code;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;
    private Double totalAmount;
    private Integer status;
    private String note;

    @ManyToOne
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;
}
