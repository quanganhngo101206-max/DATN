package com.skysport.datn.service;

import com.skysport.datn.dto.ShippingFeeResponse;
import com.skysport.datn.entity.Province;
import com.skysport.datn.repository.ProvinceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShippingFeeService {

    // 6 mức phí theo khu vực địa lý, lấy Hà Nội làm điểm xuất hàng.
    private static final BigDecimal SAME_PROVINCE_FEE = BigDecimal.valueOf(20_000);
    private static final BigDecimal NEARBY_PROVINCE_FEE = BigDecimal.valueOf(25_000);
    private static final BigDecimal NORTH_PROVINCE_FEE = BigDecimal.valueOf(30_000);
    private static final BigDecimal CENTRAL_PROVINCE_FEE = BigDecimal.valueOf(40_000);
    private static final BigDecimal SOUTH_PROVINCE_FEE = BigDecimal.valueOf(45_000);
    private static final BigDecimal REMOTE_AREA_FEE = BigDecimal.valueOf(50_000);
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = BigDecimal.valueOf(800_000);

    // Province ID theo dữ liệu hiện tại của DB.
    private static final Integer WAREHOUSE_PROVINCE_ID = 1; // Thành phố Hà Nội

    // Giáp Hà Nội theo bộ Province ID hiện tại.
    private static final Set<Integer> NEARBY_PROVINCE_IDS = Set.of(
            19, // Thái Nguyên
            24, // Bắc Ninh
            25, // Phú Thọ
            33  // Hưng Yên
    );

    // Miền Bắc còn lại.
    private static final Set<Integer> NORTH_PROVINCE_IDS = Set.of(
            8,  // Tuyên Quang
            15, // Lào Cai
            20, // Lạng Sơn
            22, // Quảng Ninh
            31, // Thành phố Hải Phòng
            37  // Ninh Bình
    );

    // Miền Trung + Tây Nguyên.
    private static final Set<Integer> CENTRAL_PROVINCE_IDS = Set.of(
            38, // Thanh Hóa
            40, // Nghệ An
            42, // Hà Tĩnh
            44, // Quảng Trị
            46, // Thành phố Huế
            48, // Thành phố Đà Nẵng
            51, // Quảng Ngãi
            52, // Gia Lai
            56, // Khánh Hòa
            66, // Đắk Lắk
            68  // Lâm Đồng
    );

    // Nam Bộ.
    private static final Set<Integer> SOUTH_PROVINCE_IDS = Set.of(
            75, // Đồng Nai
            79, // Thành phố Hồ Chí Minh
            80, // Tây Ninh
            82, // Đồng Tháp
            86, // Vĩnh Long
            91, // An Giang
            92, // Thành phố Cần Thơ
            96  // Cà Mau
    );

    // Miền núi cực xa phía Bắc.
    private static final Set<Integer> REMOTE_PROVINCE_IDS = Set.of(
            4,  // Cao Bằng
            11, // Điện Biên
            12, // Lai Châu
            14  // Sơn La
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

        BigDecimal baseFee = getBaseFee(province.getId());
        boolean freeShipping = subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0;
        BigDecimal finalFee = freeShipping ? BigDecimal.ZERO : baseFee;
        String zone = getZone(province.getId());

        return ShippingFeeResponse.builder()
                .provinceId(province.getId())
                .provinceName(province.getName())
                .zone(zone)
                .subtotal(subtotal)
                .baseFee(baseFee)
                .shippingFee(finalFee)
                .freeShippingThreshold(FREE_SHIPPING_THRESHOLD)
                .freeShipping(freeShipping)
                .message(buildMessage(freeShipping, province.getName(), baseFee))
                .build();
    }

    private BigDecimal getBaseFee(Integer provinceId) {
        if (WAREHOUSE_PROVINCE_ID.equals(provinceId)) {
            return SAME_PROVINCE_FEE;
        }
        if (NEARBY_PROVINCE_IDS.contains(provinceId)) {
            return NEARBY_PROVINCE_FEE;
        }
        if (REMOTE_PROVINCE_IDS.contains(provinceId)) {
            return REMOTE_AREA_FEE;
        }
        if (NORTH_PROVINCE_IDS.contains(provinceId)) {
            return NORTH_PROVINCE_FEE;
        }
        if (CENTRAL_PROVINCE_IDS.contains(provinceId)) {
            return CENTRAL_PROVINCE_FEE;
        }
        if (SOUTH_PROVINCE_IDS.contains(provinceId)) {
            return SOUTH_PROVINCE_FEE;
        }
        // Không âm thầm tính sai phí nếu DB có Province ID mới nhưng chưa cấu hình.
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Chưa cấu hình phí vận chuyển cho tỉnh/thành phố này"
        );
    }

    private String getZone(Integer provinceId) {
        if (WAREHOUSE_PROVINCE_ID.equals(provinceId)) {
            return "SAME_PROVINCE";
        }
        if (NEARBY_PROVINCE_IDS.contains(provinceId)) {
            return "NEARBY_PROVINCE";
        }
        if (REMOTE_PROVINCE_IDS.contains(provinceId)) {
            return "REMOTE_AREA";
        }
        if (NORTH_PROVINCE_IDS.contains(provinceId)) {
            return "NORTH_PROVINCE";
        }
        if (CENTRAL_PROVINCE_IDS.contains(provinceId)) {
            return "CENTRAL_PROVINCE";
        }
        if (SOUTH_PROVINCE_IDS.contains(provinceId)) {
            return "SOUTH_PROVINCE";
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Chưa cấu hình khu vực vận chuyển cho tỉnh/thành phố này"
        );
    }

    private String buildMessage(
            boolean freeShipping,
            String provinceName,
            BigDecimal baseFee) {
        if (freeShipping) {
            return "Miễn phí vận chuyển (Từ CĐ FPT Polytechnic)";
        }
        return "Phí giao từ Trường Cao đẳng FPT đến " + provinceName + ": " + baseFee.toPlainString() + "đ";
    }
}