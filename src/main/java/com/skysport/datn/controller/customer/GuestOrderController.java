package com.skysport.datn.controller.customer;

import com.skysport.datn.entity.Bill;
import com.skysport.datn.entity.BillDetail;
import com.skysport.datn.entity.Image;
import com.skysport.datn.repository.BillDetailRepository;
import com.skysport.datn.repository.BillRepository;
import com.skysport.datn.repository.ImageRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class GuestOrderController {

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private BillDetailRepository billDetailRepository;

    @Autowired
    private ImageRepository imageRepository;

    // Hiển thị form tra cứu đơn hàng (cho guest)
    @GetMapping("/track-order")
    public String showTrackForm() {
        return "customer/order/track";
    }

    // Xử lý tra cứu đơn hàng bằng mã đơn + số điện thoại
    @PostMapping("/track-order")
    public String trackOrder(@RequestParam String orderCode,
                             @RequestParam String phoneNumber,
                             Model model) {

        Bill bill = billRepository.findByCode(orderCode);

        if (bill == null) {
            model.addAttribute("error", "Không tìm thấy đơn hàng với mã này!");
            return "customer/order/track";
        }

        if (bill.getCustomer() == null ||
                !bill.getCustomer().getPhoneNumber().equals(phoneNumber)) {
            model.addAttribute("error", "Số điện thoại không khớp với đơn hàng!");
            return "customer/order/track";
        }

        List<BillDetail> details = billDetailRepository.findByBillId(bill.getId());

        model.addAttribute("bill", bill);
        model.addAttribute("details", details);
        model.addAttribute("productImageMap", buildImageMap(details));
        return "customer/order/guest-detail";
    }

    // Xem nhanh đơn hàng vừa đặt (dùng session, không cần nhập lại)
    @GetMapping("/guest/last-order")
    public String viewLastOrder(HttpSession session, Model model) {
        String orderCode = (String) session.getAttribute("lastOrderCode");
        String phoneNumber = (String) session.getAttribute("lastOrderPhone");

        if (orderCode == null || phoneNumber == null) {
            return "redirect:/track-order";
        }

        Bill bill = billRepository.findByCode(orderCode);
        if (bill == null) {
            return "redirect:/track-order";
        }

        List<BillDetail> details = billDetailRepository.findByBillId(bill.getId());

        model.addAttribute("bill", bill);
        model.addAttribute("details", details);
        model.addAttribute("productImageMap", buildImageMap(details));
        model.addAttribute("isRecent", true);
        return "customer/order/guest-detail";
    }

    // Xây dựng map: productId -> imageUrl để dùng trong template
    private Map<Integer, String> buildImageMap(List<BillDetail> details) {
        Map<Integer, String> map = new HashMap<>();
        for (BillDetail d : details) {
            if (d.getProductDetail() != null && d.getProductDetail().getProduct() != null) {
                Integer productId = d.getProductDetail().getProduct().getId();
                if (!map.containsKey(productId)) {
                    List<Image> images = imageRepository.findByProductId(productId);
                    map.put(productId, images.isEmpty() ? null : images.get(0).getLink());
                }
            }
        }
        return map;
    }
}