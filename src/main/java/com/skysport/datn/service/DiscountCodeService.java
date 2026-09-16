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
            if (code == null || code.isBlank()) {
                return null;
            }

            return discountCodeRepository.findByCode(code.trim()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public void save(DiscountCode discountCode) {
        validate(discountCode);

        discountCode.setCode(discountCode.getCode().trim().toUpperCase());
        discountCode.setDeleteFlag(false);

        if (discountCode.getUsedCount() == null) {
            discountCode.setUsedCount(0);
        }

        discountCodeRepository.save(discountCode);
    }

    public void update(DiscountCode discountCode) {
        if (discountCode == null || discountCode.getId() == null) {
            throw new RuntimeException("Mã giảm giá không hợp lệ!");
        }

        DiscountCode old = findById(discountCode.getId());

        if (old == null || Boolean.TRUE.equals(old.getDeleteFlag())) {
            throw new RuntimeException("Mã giảm giá không tồn tại!");
        }

        validate(discountCode);

        discountCode.setCode(discountCode.getCode().trim().toUpperCase());
        discountCode.setDeleteFlag(false);

        // Không cho form sửa số lượt đã sử dụng
        discountCode.setUsedCount(
                old.getUsedCount() != null ? old.getUsedCount() : 0
        );

        discountCodeRepository.save(discountCode);
    }

    public String validateCode(String code, Integer excludeId) {
        if (code == null || code.isBlank()) {
            return "Vui lòng nhập mã giảm giá!";
        }

        String normalizedCode = code.trim().toUpperCase();

        DiscountCode existing = findByCode(normalizedCode);

        if (existing != null
                && !Boolean.TRUE.equals(existing.getDeleteFlag())
                && (excludeId == null || !existing.getId().equals(excludeId))) {
            return "Mã giảm giá \"" + normalizedCode + "\" đã tồn tại!";
        }

        return null;
    }

    private void validate(DiscountCode discountCode) {
        if (discountCode == null) {
            throw new RuntimeException("Mã giảm giá không hợp lệ!");
        }

        if (discountCode.getCode() == null
                || discountCode.getCode().isBlank()) {
            throw new RuntimeException("Vui lòng nhập mã giảm giá!");
        }

        String code = discountCode.getCode().trim();

        if (code.length() < 3) {
            throw new RuntimeException("Mã giảm giá phải có ít nhất 3 ký tự!");
        }

        if (discountCode.getStatus() != null
                && discountCode.getStatus() != 0
                && discountCode.getStatus() != 1
                && discountCode.getStatus() != 2) {
            throw new RuntimeException("Trạng thái mã giảm giá không hợp lệ!");
        }

        if (discountCode.getType() == null
                || (discountCode.getType() != 1
                && discountCode.getType() != 2)) {
            throw new RuntimeException("Loại giảm giá không hợp lệ!");
        }

        if (discountCode.getStartDate() == null
                || discountCode.getEndDate() == null) {
            throw new RuntimeException(
                    "Vui lòng nhập đầy đủ ngày bắt đầu và ngày kết thúc!"
            );
        }

        if (discountCode.getEndDate().isBefore(discountCode.getStartDate())) {
            throw new RuntimeException(
                    "Ngày kết thúc phải sau ngày bắt đầu!"
            );
        }

        if (discountCode.getMinimumAmountInCart() != null
                && discountCode.getMinimumAmountInCart() < 0) {
            throw new RuntimeException(
                    "Giá trị đơn hàng tối thiểu không được nhỏ hơn 0!"
            );
        }

        if (discountCode.getMaximumUsage() != null
                && discountCode.getMaximumUsage() <= 0) {
            throw new RuntimeException(
                    "Số lượt sử dụng tối đa phải lớn hơn 0!"
            );
        }

        if (discountCode.getMaximumAmount() != null
                && discountCode.getMaximumAmount() <= 0) {
            throw new RuntimeException(
                    "Mức giảm tối đa phải lớn hơn 0!"
            );
        }

        if (discountCode.getType() == 1) {
            if (discountCode.getDiscountAmount() == null
                    || discountCode.getDiscountAmount() <= 0) {
                throw new RuntimeException(
                        "Số tiền giảm phải lớn hơn 0!"
                );
            }
        }

        if (discountCode.getType() == 2) {
            if (discountCode.getPercentage() == null
                    || discountCode.getPercentage() <= 0
                    || discountCode.getPercentage() > 100) {
                throw new RuntimeException(
                        "Phần trăm giảm phải lớn hơn 0 và không vượt quá 100%!"
                );
            }
        }
    }

    public String validate(String code, Double orderAmount) {
        return validate(code, orderAmount, null);
    }

    public String validate(String code, Double orderAmount, Integer customerId) {
        DiscountCode discount = findByCode(code);

        if (discount == null || Boolean.TRUE.equals(discount.getDeleteFlag())) {
            return "Mã giảm giá không tồn tại!";
        }

        if (discount.getStatus() == null || discount.getStatus() != 1) {
            return "Mã giảm giá hiện không hoạt động!";
        }

        if (orderAmount == null || orderAmount < 0) {
            return "Giá trị đơn hàng không hợp lệ!";
        }

        LocalDateTime now = LocalDateTime.now();

        if (discount.getStartDate() != null
                && now.isBefore(discount.getStartDate())) {
            return "Mã giảm giá chưa có hiệu lực!";
        }

        if (discount.getEndDate() != null
                && now.isAfter(discount.getEndDate())) {
            return "Mã giảm giá đã hết hạn!";
        }

        if (discount.getMaximumUsage() != null
                && discount.getUsedCount() != null
                && discount.getUsedCount() >= discount.getMaximumUsage()) {
            return "Mã giảm giá đã hết lượt sử dụng!";
        }

        if (discount.getMinimumAmountInCart() != null
                && orderAmount < discount.getMinimumAmountInCart()) {
            return "Đơn hàng chưa đạt giá trị tối thiểu "
                    + discount.getMinimumAmountInCart() + "đ!";
        }

        if (customerId != null) {
            long used = billRepository
                    .countByCustomerIdAndDiscountCodeIdExcludingCancelled(
                            customerId,
                            discount.getId()
                    );

            if (used > 0) {
                return "Bạn đã sử dụng mã giảm giá này rồi!";
            }
        }

        Double calculatedDiscount = calculateDiscount(discount, orderAmount);

        if (calculatedDiscount <= 0) {
            return "Mã giảm giá không có giá trị giảm!";
        }

        if (calculatedDiscount > orderAmount) {
            return "Mức giảm giá không hợp lệ!";
        }

        return "OK";
    }

    public String getDisplayStatus(DiscountCode discount) {
        if (discount == null) {
            return "Không xác định";
        }

        if (discount.getStatus() != null
                && discount.getStatus() == 0) {
            return "Chờ duyệt";
        }

        if (discount.getStatus() != null
                && discount.getStatus() == 2) {
            return "Tạm dừng";
        }

        if (discount.getStatus() == null
                || discount.getStatus() != 1) {
            return "Không hoạt động";
        }

        LocalDateTime now = LocalDateTime.now();

        if (discount.getStartDate() != null
                && now.isBefore(discount.getStartDate())) {
            return "Chưa bắt đầu";
        }

        if (discount.getEndDate() != null
                && now.isAfter(discount.getEndDate())) {
            return "Hết hạn";
        }

        if (discount.getMaximumUsage() != null
                && discount.getUsedCount() != null
                && discount.getUsedCount() >= discount.getMaximumUsage()) {
            return "Hết lượt";
        }

        return "Hoạt động";
    }

    public Double calculateDiscount(DiscountCode discount, Double orderAmount) {
        if (discount == null || orderAmount == null || orderAmount < 0) {
            return 0.0;
        }

        if (discount.getType() == null) {
            return 0.0;
        }

        Double discountAmount = 0.0;

        if (discount.getType() == 1) {
            discountAmount = discount.getDiscountAmount() != null
                    ? discount.getDiscountAmount()
                    : 0.0;
        } else if (discount.getType() == 2) {
            if (discount.getPercentage() == null) {
                return 0.0;
            }

            discountAmount = orderAmount
                    * discount.getPercentage()
                    / 100;

            if (discount.getMaximumAmount() != null
                    && discountAmount > discount.getMaximumAmount()) {
                discountAmount = discount.getMaximumAmount();
            }
        }

        if (discountAmount > orderAmount) {
            discountAmount = orderAmount;
        }

        return Math.max(discountAmount, 0.0);
    }

    @org.springframework.transaction.annotation.Transactional
    public void incrementUsage(Integer id) {
        int updated = discountCodeRepository.incrementUsedCount(id);

        if (updated == 0) {
            throw new IllegalStateException(
                    "Mã giảm giá đã hết lượt sử dụng hoặc không tồn tại!"
            );
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void decrementUsage(Integer id) {
        if (id != null) {
            discountCodeRepository.decrementUsedCount(id);
        }
    }
}