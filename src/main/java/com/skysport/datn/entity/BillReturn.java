package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Bill_return")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"bill", "returnRequest"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
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