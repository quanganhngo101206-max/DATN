package com.skysport.datn.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Bill")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"billDetails", "customer", "discountCode", "paymentMethod"})
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
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

    @Column(name = "pos_status")
    private Integer posStatus;

    /**
     * Mã tham chiếu giao dịch (vnp_TxnRef) của lần thanh toán VNPay gần nhất
     * cho hóa đơn này. Dùng để đối soát khi VNPay gọi về Return URL / IPN,
     * vì vnp_TxnRef là mã VNPay biết, không phải billId nội bộ của hệ thống.
     * Mỗi lần khách bấm "thanh toán lại" sẽ sinh txnRef mới, ghi đè giá trị cũ.
     */
    @Column(name = "vnp_txn_ref")
    private String vnpTxnRef;

    @ManyToOne
    @JoinColumn(name = "discount_code_id")
    private DiscountCode discountCode;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "payment_id")
    private Payment paymentMethod;

    // LAZY: tránh Hibernate kéo toàn bộ BillDetail mỗi khi load Bill
    // (đặc biệt nguy hiểm với billRepository.findAll() trong StatisticsService)
    @OneToMany(mappedBy = "bill", fetch = FetchType.LAZY)
    private List<BillDetail> billDetails = new ArrayList<>();
}
