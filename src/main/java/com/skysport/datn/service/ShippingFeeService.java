package com.skysport.datn.service;

import com.skysport.datn.dto.ShippingFeeResponse;
import com.skysport.datn.entity.Province;
import com.skysport.datn.repository.ProvinceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShippingFeeService {

    private static final BigDecimal SAME_PROVINCE_FEE = BigDecimal.valueOf(20_000);
    private static final BigDecimal NEARBY_PROVINCE_FEE = BigDecimal.valueOf(25_000);
    private static final BigDecimal OTHER_PROVINCE_FEE = BigDecimal.valueOf(35_000);
    private static final BigDecimal REMOTE_AREA_FEE = BigDecimal.valueOf(50_000);
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = BigDecimal.valueOf(800_000);

    private static final String WAREHOUSE_PROVINCE = "ha noi";
    private static final Set<String> NEARBY_PROVINCES = Set.of("bac ninh");

    /*
     * Danh sách này có thể điều chỉnh theo chính sách giao hàng thực tế của shop.
     * Chỉ cần thêm tên tỉnh/thành phố đã chuẩn hóa vào Set này.
     */
    private static final Set<String> REMOTE_PROVINCES = Set.of(
            "ha giang",
            "cao bang",
            "bac kan",
            "lai chau",
            "dien bien",
            "son la",
            "kon tum",
            "phu quoc",
            "con dao"
    );

    private final ProvinceRepository provinceRepository;

    public ShippingFeeResponse calculate(Integer provinceId, BigDecimal subtotal) {
        if (provinceId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn tỉnh/thành phố nhận hàng"
            );
        }

        if (subtotal == null || subtotal.signum() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Giá trị đơn hàng không hợp lệ"
            );
        }

        Province province = provinceRepository.findById(provinceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Không tìm thấy tỉnh/thành phố nhận hàng"
                ));

        String displayProvinceName = province.getName();
        String normalizedProvinceName = normalize(displayProvinceName);
        BigDecimal baseFee = getBaseFee(normalizedProvinceName);
        boolean freeShipping = subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0;
        BigDecimal finalFee = freeShipping ? BigDecimal.ZERO : baseFee;
        String zone = getZone(normalizedProvinceName);

        return ShippingFeeResponse.builder()
                .provinceId(province.getId())
                .provinceName(province.getName())
                .zone(zone)
                .subtotal(subtotal)
                .baseFee(baseFee)
                .shippingFee(finalFee)
                .freeShippingThreshold(FREE_SHIPPING_THRESHOLD)
                .freeShipping(freeShipping)
                .message(buildMessage(freeShipping, displayProvinceName, baseFee))
                .build();
    }

    private BigDecimal getBaseFee(String provinceName) {
        if (WAREHOUSE_PROVINCE.equals(provinceName)) {
            return SAME_PROVINCE_FEE;
        }
        if (NEARBY_PROVINCES.contains(provinceName)) {
            return NEARBY_PROVINCE_FEE;
        }
        if (REMOTE_PROVINCES.contains(provinceName)) {
            return REMOTE_AREA_FEE;
        }
        return OTHER_PROVINCE_FEE;
    }

    private String getZone(String provinceName) {
        if (WAREHOUSE_PROVINCE.equals(provinceName)) {
            return "SAME_PROVINCE";
        }
        if (NEARBY_PROVINCES.contains(provinceName)) {
            return "NEARBY_PROVINCE";
        }
        if (REMOTE_PROVINCES.contains(provinceName)) {
            return "REMOTE_AREA";
        }
        return "OTHER_PROVINCE";
    }

    private String buildMessage(
            boolean freeShipping,
            String provinceName,
            BigDecimal baseFee
    ) {
        if (freeShipping) {
            return "Đơn hàng từ 800.000đ được miễn phí vận chuyển";
        }
        return "Phí vận chuyển đến " + provinceName + ": "
                + baseFee.toPlainString() + "đ";
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replace("đ", "d")
                .replaceAll("^(tinh|thanh pho|tp\\.)\\s+", "")
                .trim();

        return normalized.replaceAll("\\s+", " ");
    }
}
