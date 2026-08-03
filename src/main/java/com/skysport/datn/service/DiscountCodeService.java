package com.skysport.datn.service;

import com.skysport.datn.entity.DiscountCode;
import com.skysport.datn.repository.BillRepository;
import com.skysport.datn.repository.DiscountCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DiscountCodeService {

    @Autowired
    private DiscountCodeRepository discountCodeRepository;

    @Autowired
    private BillRepository billRepository;

    public List<DiscountCode> findAll() {
        try {
            return discountCodeRepository.findByDeleteFlagFalse();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<DiscountCode> findByStatus(Integer status) {
        try {
            return discountCodeRepository.findByStatusAndDeleteFlagFalse(status);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public DiscountCode findById(Integer id) {
        try {
            return discountCodeRepository.findById(id).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public DiscountCode findByCode(String code) {
        try {
            return discountCodeRepository.findByCode(code).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public void save(DiscountCode discountCode) {
        discountCode.setDeleteFlag(false);
        if (discountCode.getUsedCount() == null) {
            discountCode.setUsedCount(0);
        }
        discountCodeRepository.save(discountCode);
    }

    public void update(DiscountCode discountCode) {
        discountCodeRepository.save(discountCode);
    }

    public void delete(Integer id) {
        DiscountCode d = findById(id);
        if (d != null) {
            d.setDeleteFlag(true);
            discountCodeRepository.save(d);
        }
    }

    public String validate(String code, Double orderAmount) {
        return validate(code, orderAmount, null);
    }

    public String validate(String code, Double orderAmount, Integer customerId) {
        DiscountCode discount = findByCode(code);
        if (discount == null || Boolean.TRUE.equals(discount.getDeleteFlag()))
            return "Mã giảm giá không tồn tại!";
        if (discount.getStatus() == null || discount.getStatus() != 1)
            return "Mã giảm giá chưa được kích hoạt!";

        LocalDateTime now = LocalDateTime.now();
        if (discount.getStartDate() != null && now.isBefore(discount.getStartDate()))
            return "Mã giảm giá chưa có hiệu lực!";
        if (discount.getEndDate() != null && now.isAfter(discount.getEndDate()))
            return "Mã giảm giá đã hết hạn!";

        // Dùng usedCount >= maximumUsage thay vì maximumUsage <= 0
        // để tránh race condition khi 2 request dùng mã cùng lúc
        if (discount.getMaximumUsage() != null && discount.getUsedCount() != null
                && discount.getUsedCount() >= discount.getMaximumUsage())
            return "Mã giảm giá đã hết lượt sử dụng!";

        if (discount.getMinimumAmountInCart() != null
                && orderAmount < discount.getMinimumAmountInCart())
            return "Đơn hàng chưa đạt giá trị tối thiểu "
                    + discount.getMinimumAmountInCart() + "đ!";

        // Mỗi khách hàng chỉ được dùng 1 lần (tính trên các đơn không bị hủy)
        if (customerId != null) {
            long used = billRepository
                    .countByCustomerIdAndDiscountCodeIdExcludingCancelled(
                            customerId, discount.getId());
            if (used > 0)
                return "Bạn đã sử dụng mã giảm giá này rồi!";
        }

        return "OK";
    }

    public Double calculateDiscount(DiscountCode discount, Double orderAmount) {
        if (discount == null || orderAmount == null) return 0.0;
        if (discount.getType() == null) return 0.0;

        Double discountAmount = 0.0;
        if (discount.getType() == 1) {
            discountAmount = discount.getDiscountAmount() != null
                    ? discount.getDiscountAmount() : 0.0;
        } else if (discount.getType() == 2) {
            if (discount.getPercentage() == null) return 0.0;
            discountAmount = orderAmount * discount.getPercentage() / 100;
            if (discount.getMaximumAmount() != null
                    && discountAmount > discount.getMaximumAmount())
                discountAmount = discount.getMaximumAmount();
        }
        return discountAmount;
    }

    public void decreaseUsage(Integer id) {
        // Dùng UPDATE atomic ở tầng DB — tránh lost update khi 2 request
        // cùng áp 1 mã giảm giá tại cùng 1 thời điểm (đúng như comment trong repository).
        discountCodeRepository.incrementUsedCount(id);
    }
}