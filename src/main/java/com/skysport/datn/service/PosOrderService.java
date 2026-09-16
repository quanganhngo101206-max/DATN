package com.skysport.datn.service;

import com.skysport.datn.entity.Bill;
import com.skysport.datn.entity.BillDetail;
import com.skysport.datn.enums.PosOrderStatus;
import com.skysport.datn.repository.BillDetailRepository;
import com.skysport.datn.repository.BillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PosOrderService {

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private BillDetailRepository billDetailRepository;

    // Lưu đơn POS
    public Bill save(Bill bill) {
        bill.setInvoiceType(2);
        return billRepository.save(bill);
    }

    // Tìm đơn POS theo id
    public Bill findById(Integer id) {
        return billRepository.findById(id).orElse(null);
    }

    // Lấy danh sách đơn POS đang chờ
    public List<Bill> findWaitingOrders() {
        return billRepository.findByInvoiceTypeAndPosStatusOrderByCreateDateDesc(
                2,
                PosOrderStatus.WAITING.getValue()
        );
    }

    // Lấy danh sách đơn POS đã hoàn thành
    public List<Bill> findCompletedOrders() {
        return billRepository.findByInvoiceTypeAndPosStatusOrderByUpdateDateDesc(
                2,
                PosOrderStatus.COMPLETED.getValue()
        );
    }

    // Chuyển đơn POS sang trạng thái chờ
    @Transactional
    public Bill markWaiting(Bill bill) {
        bill.setInvoiceType(2);
        bill.setPosStatus(PosOrderStatus.WAITING.getValue());
        bill.setUpdateDate(LocalDateTime.now());

        return billRepository.save(bill);
    }

    // Thêm chi tiết đơn POS
    public BillDetail addDetail(BillDetail detail) {
        return billDetailRepository.save(detail);
    }

    // Hủy đơn POS đang chờ
    @Transactional
    public Bill cancel(Bill bill) {
        if (!PosOrderStatus.WAITING.matches(bill.getPosStatus())) {
            throw new RuntimeException("Chỉ có thể hủy đơn POS đang chờ!");
        }

        bill.setPosStatus(PosOrderStatus.CANCELLED.getValue());
        bill.setUpdateDate(LocalDateTime.now());

        return billRepository.save(bill);
    }

    // Hết hạn đơn POS đang chờ
    @Transactional
    public Bill expire(Bill bill) {
        if (!PosOrderStatus.WAITING.matches(bill.getPosStatus())) {
            throw new RuntimeException("Chỉ có thể hết hạn đơn POS đang chờ!");
        }

        bill.setPosStatus(PosOrderStatus.EXPIRED.getValue());
        bill.setUpdateDate(LocalDateTime.now());

        return billRepository.save(bill);
    }
}