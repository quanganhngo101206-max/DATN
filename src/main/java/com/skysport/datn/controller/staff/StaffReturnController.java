package com.skysport.datn.controller.staff;

import com.skysport.datn.entity.*;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.enums.ReturnRequestStatus;
import com.skysport.datn.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/staff/return")
public class StaffReturnController {

    @Autowired private BillReturnRequestRepository requestRepository;
    @Autowired private BillReturnRequestDetailRepository requestDetailRepository;
    @Autowired private BillReturnRepository billReturnRepository;
    @Autowired private BillRepository billRepository;
    @Autowired private ProductDetailRepository productDetailRepository;
    @Autowired private StaffRepository staffRepository;

    // Danh sách yêu cầu hoàn trả
    @GetMapping
    public String list(@RequestParam(required = false) Integer status, Model model, HttpSession session) {

        Account account = (Account) session.getAttribute("account");
        if (account != null) {
            Staff staff = staffRepository.findByAccountId(account.getId());
            model.addAttribute("staff", staff);
        }

        List<ReturnRequest> requests;
        if (status != null) {
            requests = requestRepository.findByStatus(status);
            model.addAttribute("currentStatus", status);
        } else {
            requests = requestRepository.findAllByOrderByCreatedDateDesc();
            model.addAttribute("currentStatus", -1);
        }
        // Đảm bảo không null để Thymeleaf không bị lỗi
        model.addAttribute("requests", requests != null ? requests : List.of());
        return "staff/return/list";
    }

    // Chi tiết yêu cầu hoàn trả
    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        ReturnRequest request = requestRepository.findById(id).orElse(null);
        if (request == null) return "redirect:/staff/return";

        // Dùng method đã sửa tên: findByBillReturnRequest_Id
        List<ReturnRequestDetail> details = requestDetailRepository.findByBillReturnRequest_Id(id);

        model.addAttribute("request", request);
        model.addAttribute("details", details != null ? details : List.of());
        return "staff/return/detail";
    }

    // Duyệt yêu cầu → tạo BillReturn, cộng tồn kho, đổi bill status = 6
    @PostMapping("/approve/{id}")
    @Transactional
    public String approve(@PathVariable Integer id,
                          @RequestParam String returnReason,
                          @RequestParam(required = false) Float percentFeeExchange,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        ReturnRequest request = requestRepository.findById(id).orElse(null);
        if (request == null || !ReturnRequestStatus.PENDING.matches(request.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMsg", "Yêu cầu không hợp lệ!");
            return "redirect:/staff/return";
        }

        List<ReturnRequestDetail> details =
                requestDetailRepository.findByBillReturnRequest_Id(id);

        // Tính tiền hoàn
        float totalRefund = 0;
        for (ReturnRequestDetail d : details) {
            float itemTotal = (d.getMomentPriceRefund() != null ? d.getMomentPriceRefund() : 0)
                    * (d.getQuantityReturn() != null ? d.getQuantityReturn() : 0);
            totalRefund += itemTotal;
        }
        float fee = percentFeeExchange != null ? percentFeeExchange : 0;
        float returnMoney = totalRefund * (1 - fee / 100);

        // Cộng lại tồn kho
        for (ReturnRequestDetail d : details) {
            if (d.getProductDetail() != null && d.getQuantityReturn() != null) {
                ProductDetail pd = productDetailRepository.findById(
                        d.getProductDetail().getId()).orElse(null);
                if (pd != null) {
                    pd.setQuantity((pd.getQuantity() != null ? pd.getQuantity() : 0)
                            + d.getQuantityReturn());
                    productDetailRepository.save(pd);
                }
            }
        }

        // Tạo BillReturn
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

        // Cập nhật bill status = trả hàng
        Bill bill = request.getBill();
        bill.setStatus(OrderStatus.RETURNING.getValue());
        billRepository.save(bill);

        // Cập nhật request status = đã duyệt
        request.setStatus(ReturnRequestStatus.APPROVED.getValue());
        requestRepository.save(request);

        redirectAttributes.addFlashAttribute("successMsg",
                "Đã duyệt hoàn trả. Hoàn tiền: "
                        + String.format("%,.0f", returnMoney) + "đ");
        return "redirect:/staff/return";
    }

    // Từ chối yêu cầu
    @PostMapping("/reject/{id}")
    public String reject(@PathVariable Integer id,
                         RedirectAttributes redirectAttributes) {
        ReturnRequest request = requestRepository.findById(id).orElse(null);
        if (request != null && ReturnRequestStatus.PENDING.matches(request.getStatus())) {
            request.setStatus(ReturnRequestStatus.REJECTED.getValue());
            requestRepository.save(request);
            redirectAttributes.addFlashAttribute("successMsg", "Đã từ chối yêu cầu hoàn trả.");
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", "Không thể từ chối yêu cầu này.");
        }
        return "redirect:/staff/return";
    }
}