package com.skysport.datn.service;

import com.skysport.datn.entity.ProductDetail;
import org.springframework.stereotype.Service;

@Service
public class ImportPriceService {

    private static final float DEFAULT_MARGIN_PERCENT = 30f;

    // Tính giá bán đề xuất từ giá nhập + tỷ lệ lợi nhuận mục tiêu
    public Float calculateSuggestedSellingPrice(ProductDetail detail, Float importPrice) {
        if (detail == null) {
            throw new RuntimeException("Biến thể sản phẩm không hợp lệ");
        }

        if (importPrice == null || importPrice < 0) {
            throw new RuntimeException("Giá nhập không hợp lệ");
        }

        Float margin = detail.getTargetMarginPercent();

        if (margin == null) {
            margin = DEFAULT_MARGIN_PERCENT;
        }

        if (margin < 0) {
            throw new RuntimeException("Tỷ lệ lợi nhuận mục tiêu không được nhỏ hơn 0");
        }

        return importPrice * (1 + margin / 100);
    }

    // Lấy tỷ lệ lợi nhuận mục tiêu
    public Float getTargetMarginPercent(ProductDetail detail) {
        if (detail == null) {
            return null;
        }

        return detail.getTargetMarginPercent() != null
                ? detail.getTargetMarginPercent()
                : DEFAULT_MARGIN_PERCENT;
    }

    // Kiểm tra giá nhập có cao hơn giá bán hiện tại hay không
    public boolean isImportPriceHigherThanSellingPrice(ProductDetail detail, Float importPrice) {
        if (detail == null || importPrice == null || detail.getPrice() == null) {
            return false;
        }

        return importPrice > detail.getPrice();
    }

    // Kiểm tra giá bán hiện tại có thấp hơn giá nhập hay không
    public boolean isSellingPriceBelowImportPrice(ProductDetail detail, Float importPrice) {
        if (detail == null || importPrice == null || detail.getPrice() == null) {
            return false;
        }

        return detail.getPrice() < importPrice;
    }

    // Kiểm tra giá đề xuất có cao hơn giá bán hiện tại hay không
    public boolean isSuggestedPriceHigherThanCurrentPrice(ProductDetail detail, Float importPrice) {
        if (detail == null || importPrice == null || detail.getPrice() == null) {
            return false;
        }

        Float suggestedPrice = calculateSuggestedSellingPrice(detail, importPrice);

        return suggestedPrice != null && suggestedPrice > detail.getPrice();
    }

    // Lấy giá bán hiện tại
    public Float getCurrentSellingPrice(ProductDetail detail) {
        if (detail == null) {
            return null;
        }

        return detail.getPrice();
    }
}