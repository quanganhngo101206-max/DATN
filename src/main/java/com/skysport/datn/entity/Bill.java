package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Bill")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String code;

    private Float promotionPrice;

    private LocalDateTime createDate;

    private Integer invoiceType;

    private LocalDateTime updateDate;

    private Integer status;

    private String billingAddress;

    private Float amount;

    private Float subtotal;

    private Float shippingFee;

    private Integer returnStatus;

    private String note;

    @ManyToOne
    @JoinColumn(name = "discount_code_id")
    private DiscountCode discountCode;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "payment_id")
    private Payment paymentMethod;

    @OneToMany(mappedBy = "bill", fetch = FetchType.EAGER)
    private List<BillDetail> billDetails = new ArrayList<>();
}
