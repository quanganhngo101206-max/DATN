package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Bill_return")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String code;

    private String returnReason;

    private String returnDetail;

    private LocalDateTime returnDate;

    private Float percentFeeExchange;

    private Float returnMoney;

    private Boolean isCancel;

    private Integer returnStatus;

    @ManyToOne
    @JoinColumn(name = "bill_id")
    private Bill bill;

    @ManyToOne
    @JoinColumn(name = "return_request_id")
    private ReturnRequest returnRequest;
}
