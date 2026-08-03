package com.skysport.datn.service;

import com.skysport.datn.entity.*;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final BillDetailRepository billDetailRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final StaffRepository staffRepository;
    private final ProductDetailRepository productDetailRepository;

    public List<Bill> findAll() {
        return billRepository.findAllByOrderByCreateDateDesc();
    }

    public List<Bill> findByStatus(Integer status) {
        return billRepository.findByStatusOrderByCreateDateDesc(status);
    }

    public Page<Bill> findAllPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return billRepository.findAllByOrderByCreateDateDesc(pageable);
    }

    public Page<Bill> findByStatusPaged(Integer status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return billRepository.findByStatusOrderByCreateDateDesc(status, pageable);
    }

    public Bill findById(Integer id) {
        return billRepository.findById(id).orElse(null);
    }

    public List<BillDetail> findDetailsByBillId(Integer billId) {
        return billDetailRepository.findByBillId(billId);
    }

    public List<OrderStatusHistory> findHistoryByBillId(Integer billId) {
        return orderStatusHistoryRepository.findByBillIdOrderByCreatedDateAsc(billId);
    }

    /**
     * Chuyển trạng thái đơn hàng theo state machine:
     * PENDING → CONFIRMED | CANCELLED
     * CONFIRMED → SHIPPING | CANCELLED
     * SHIPPING → COMPLETED   (RETURNING đi qua BillReturnRequest)
     */
    @Transactional
    public boolean updateStatus(Integer billId, Integer newStatus, String note, Account account) {
        Bill bill = findById(billId);
        if (bill == null) return false;

        OrderStatus current = OrderStatus.of(bill.getStatus());
        OrderStatus next    = OrderStatus.of(newStatus);
        if (current == null || next == null) return false;

        boolean valid = switch (current) {
            case PENDING   -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.SHIPPING  || next == OrderStatus.CANCELLED;
            case SHIPPING  -> next == OrderStatus.COMPLETED;
            default        -> false;
        };

        if (!valid) return false;

        if (next == OrderStatus.CANCELLED) {
            restockBillItems(bill);
        }

        bill.setStatus(next.getValue());
        bill.setUpdateDate(LocalDateTime.now());
        billRepository.save(bill);

        Staff staff = null;
        if (account != null) {
            staff = staffRepository.findByAccountId(account.getId());
        }

        OrderStatusHistory history = new OrderStatusHistory();
        history.setBill(bill);
        history.setStatus(next.getValue());
        history.setNote(note != null && !note.isBlank() ? note : next.getLabel());
        history.setCreatedDate(LocalDateTime.now());
        history.setStaff(staff);
        orderStatusHistoryRepository.save(history);

        return true;
    }

    private void restockBillItems(Bill bill) {
        List<BillDetail> details = billDetailRepository.findByBillId(bill.getId());
        for (BillDetail detail : details) {
            if (detail.getProductDetail() == null || detail.getQuantity() == null) continue;
            ProductDetail pd = productDetailRepository.findById(detail.getProductDetail().getId()).orElse(null);
            if (pd != null) {
                pd.setQuantity((pd.getQuantity() != null ? pd.getQuantity() : 0) + detail.getQuantity());
                productDetailRepository.save(pd);
            }
        }
    }

    public String getStatusText(Integer status) {
        OrderStatus os = OrderStatus.of(status);
        return os != null ? os.getLabel() : "Không xác định";
    }
}