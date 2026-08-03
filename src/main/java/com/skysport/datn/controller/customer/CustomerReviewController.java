package com.skysport.datn.controller.customer;

import com.skysport.datn.entity.*;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class CustomerReviewController {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private BillRepository billRepository;
    @Autowired
    private BillDetailRepository billDetailRepository;

    @PostMapping("/products/{id}/review")
    public String submitReview(@PathVariable Integer id,
                               @RequestParam Integer rating,
                               @RequestParam(required = false) String comment,
                               HttpSession session,
                               RedirectAttributes ra) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) {
            return "redirect:/login";
        }

        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            return "redirect:/products";
        }

        Customer customer = customerRepository.findByAccountId(account.getId());
        if (customer == null) {
            ra.addFlashAttribute("error", "Không tìm thấy thông tin khách hàng!");
            return "redirect:/products/" + id;
        }

        // Đã đánh giá rồi -> không cho đánh giá lại
        Review existing = reviewRepository.findByProduct_IdAndCustomer_Id(id, customer.getId());
        if (existing != null) {
            ra.addFlashAttribute("error", "Bạn đã đánh giá sản phẩm này rồi!");
            return "redirect:/products/" + id;
        }

        // Kiểm tra đã mua sản phẩm trong đơn hoàn thành chưa
        List<Bill> bills = billRepository.findByCustomerId(customer.getId());
        boolean purchased = false;
        for (Bill bill : bills) {
            if (!OrderStatus.COMPLETED.matches(bill.getStatus())) continue;
            List<BillDetail> billDetails = billDetailRepository.findByBillId(bill.getId());
            if (billDetails.stream().anyMatch(bd -> bd.getProductDetail() != null
                    && bd.getProductDetail().getProduct() != null
                    && bd.getProductDetail().getProduct().getId().equals(id))) {
                purchased = true;
                break;
            }
        }

        if (!purchased) {
            ra.addFlashAttribute("error", "Bạn cần mua và nhận sản phẩm này (đơn đã hoàn thành) trước khi đánh giá!");
            return "redirect:/products/" + id;
        }

        // Validate rating
        if (rating == null || rating < 1 || rating > 5) {
            ra.addFlashAttribute("error", "Vui lòng chọn số sao đánh giá hợp lệ (1-5)!");
            return "redirect:/products/" + id;
        }

        Review review = Review.builder()
                .rating(rating)
                .comment(comment)
                .createdDate(LocalDateTime.now())
                .customer(customer)
                .product(product)
                .build();
        reviewRepository.save(review);

        ra.addFlashAttribute("successMsg", "Cảm ơn bạn đã đánh giá sản phẩm!");
        return "redirect:/products/" + id;
    }
}