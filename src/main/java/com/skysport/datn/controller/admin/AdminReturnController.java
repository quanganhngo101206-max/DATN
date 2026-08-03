package com.skysport.datn.controller.admin;

import com.skysport.datn.entity.*;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.enums.ReturnRequestStatus;
import com.skysport.datn.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/admin/return")
public class AdminReturnController {

    @Autowired private BillReturnRequestRepository requestRepository;
    @Autowired private BillReturnRequestDetailRepository requestDetailRepository;
    @Autowired private BillReturnRepository billReturnRepository;
    @Autowired private BillRepository billRepository;
    @Autowired private ProductDetailRepository productDetailRepository;

    @GetMapping
    public String list(@RequestParam(required = false) Integer status, Model model) {
        List<ReturnRequest> requests = (status != null)
                ? requestRepository.findByStatus(status)
                : requestRepository.findAllByOrderByCreatedDateDesc();
        model.addAttribute("requests", requests != null ? requests : List.of());
        model.addAttribute("currentStatus", status != null ? status : -1);
        model.addAttribute("pendingCount", requestRepository.findByStatus(1).size());
        return "admin/return/list";
    }

    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        ReturnRequest request = requestRepository.findById(id).orElse(null);
        if (request == null) return "redirect:/admin/return";
        model.addAttribute("request", request);
        model.addAttribute("details", requestDetailRepository.findByBillReturnRequest_Id(id));
        return "admin/return/detail";
    }

    @PostMapping("/approve/{id}")
    @Transactional
    public String approve(@PathVariable Integer id,
                          @RequestParam String returnReason,
                          @RequestParam(required = false) Float percentFeeExchange,
                          RedirectAttributes ra) {
        ReturnRequest request = requestRepository.findById(id).orElse(null);
        if (request == null || !ReturnRequestStatus.PENDING.matches(request.getStatus())) {
            ra.addFlashAttribute("errorMsg", "Yêu cầu không hợp lệ!");
            return "redirect:/admin/return";
        }

        List<ReturnRequestDetail> details = requestDetailRepository.findByBillReturnRequest_Id(id);

        float totalRefund = 0;
        for (ReturnRequestDetail d : details) {
            totalRefund += (d.getMomentPriceRefund() != null ? d.getMomentPriceRefund() : 0)
                    * (d.getQuantityReturn() != null ? d.getQuantityReturn() : 0);
        }
        float fee = percentFeeExchange != null ? percentFeeExchange : 0;
        float returnMoney = totalRefund * (1 - fee / 100);

        for (ReturnRequestDetail d : details) {
            if (d.getProductDetail() == null || d.getQuantityReturn() == null) continue;
            ProductDetail pd = productDetailRepository.findById(d.getProductDetail().getId()).orElse(null);
            if (pd != null) {
                pd.setQuantity((pd.getQuantity() != null ? pd.getQuantity() : 0) + d.getQuantityReturn());
                productDetailRepository.save(pd);
            }
        }

        BillReturn billReturn = BillReturn.builder()
                .code("TR" + System.currentTimeMillis())
                .returnReason(returnReason)
                .returnDate(LocalDateTime.now())
                .percentFeeExchange(fee)
                .returnMoney(returnMoney)
                .isCancel(false)
                .returnStatus(1)
                .bill(request.getBill())
                .returnRequest(request)
                .build();
        billReturnRepository.save(billReturn);

        request.getBill().setStatus(OrderStatus.RETURNING.getValue());
        billRepository.save(request.getBill());

        request.setStatus(ReturnRequestStatus.APPROVED.getValue());
        requestRepository.save(request);

        ra.addFlashAttribute("successMsg", "Đã duyệt hoàn trả. Hoàn tiền: "
                + String.format("%,.0f", returnMoney) + "đ");
        return "redirect:/admin/return";
    }

    @PostMapping("/reject/{id}")
    public String reject(@PathVariable Integer id, RedirectAttributes ra) {
        ReturnRequest request = requestRepository.findById(id).orElse(null);
        if (request != null && ReturnRequestStatus.PENDING.matches(request.getStatus())) {
            request.setStatus(ReturnRequestStatus.REJECTED.getValue());
            requestRepository.save(request);
            ra.addFlashAttribute("successMsg", "Đã từ chối yêu cầu hoàn trả.");
        } else {
            ra.addFlashAttribute("errorMsg", "Không thể từ chối yêu cầu này.");
        }
        return "redirect:/admin/return";
    }
}