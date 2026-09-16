package com.skysport.datn.controller;

import com.skysport.datn.entity.BillDetail;
import com.skysport.datn.entity.Customer;
import com.skysport.datn.entity.DiscountCode;
import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.enums.PosOrderStatus;
import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.Bill;
import com.skysport.datn.entity.Staff;
import com.skysport.datn.repository.BillDetailRepository;
import com.skysport.datn.repository.CustomerRepository;
import com.skysport.datn.repository.ProductDetailRepository;
import com.skysport.datn.repository.StaffRepository;
import com.skysport.datn.service.DiscountCodeService;
import com.skysport.datn.service.PosOrderService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/pos")
public class PosOrderController {

    @Autowired
    private BillDetailRepository billDetailRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductDetailRepository productDetailRepository;

    @Autowired
    private DiscountCodeService discountCodeService;

    @Autowired
    private PosOrderService posOrderService;

    @Autowired
    private StaffRepository staffRepository;

    // ===== Trang POS =====
    @GetMapping("/create")
    public String createForm(HttpSession session, Model model) {
        Account account = (Account) session.getAttribute("account");

        if (account != null) {
            Staff staff = staffRepository.findByAccountId(account.getId());
            model.addAttribute("staff", staff);
        }

        model.addAttribute("waitingOrders", posOrderService.findWaitingOrders());

        // Tạm thời dùng lại giao diện POS cũ
        // Sau khi luồng POS mới hoàn chỉnh sẽ chuyển sang templates/pos/create.html
        return "staff/order/create";
    }

    // ===== Danh sách đơn POS đang chờ =====
    @GetMapping("/waiting")
    public String waitingOrders(Model model) {
        model.addAttribute("waitingOrders", posOrderService.findWaitingOrders());
        return "pos/waiting";
    }

    // ===== Tạo đơn POS đang chờ =====
    @PostMapping("/waiting")
    public String createWaitingOrder(
            @RequestParam(required = false) Integer customerId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerPhone,
            @RequestParam Integer paymentMethodId,
            @RequestParam(required = false) String discountCode,
            @RequestParam List<Integer> productDetailIds,
            @RequestParam List<Integer> quantities,
            HttpSession session,
            RedirectAttributes ra) {

        try {
            if (productDetailIds == null
                    || quantities == null
                    || productDetailIds.isEmpty()
                    || productDetailIds.size() != quantities.size()) {
                throw new RuntimeException("Dữ liệu sản phẩm không hợp lệ!");
            }

            for (Integer quantity : quantities) {
                if (quantity == null || quantity <= 0) {
                    throw new RuntimeException("Số lượng sản phẩm phải lớn hơn 0!");
                }
            }

            Customer customer = null;

            if (customerId != null) {
                customer = customerRepository.findById(customerId).orElse(null);
            }

            if (customer == null
                    && customerPhone != null
                    && !customerPhone.isBlank()) {
                customer = customerRepository.findByPhoneNumber(customerPhone).orElse(null);
            }

            if (customer == null) {
                customer = new Customer();
                customer.setName(
                        customerName != null && !customerName.isBlank()
                                ? customerName
                                : "Khách lẻ"
                );
                customer.setPhoneNumber(
                        customerPhone != null ? customerPhone : ""
                );
                customer.setCode(
                        "KH" + java.util.UUID.randomUUID()
                                .toString()
                                .replace("-", "")
                                .substring(0, 8)
                                .toUpperCase()
                );
                customer = customerRepository.save(customer);
            }

            double subtotal = 0;

            Bill bill = new Bill();
            bill.setCreateDate(LocalDateTime.now());
            bill.setUpdateDate(LocalDateTime.now());
            bill.setStatus(OrderStatus.PENDING.getValue());
            bill.setInvoiceType(2);
            bill.setPosStatus(PosOrderStatus.WAITING.getValue());
            bill.setCustomer(customer);
            bill.setBillingAddress("Tại quầy");
            bill.setShippingFee(0f);

            for (int i = 0; i < productDetailIds.size(); i++) {

                ProductDetail pd = productDetailRepository
                        .findById(productDetailIds.get(i))
                        .orElse(null);

                if (pd == null) {
                    throw new RuntimeException("Sản phẩm không tồn tại!");
                }

                if (!pd.isSellable()) {
                    throw new RuntimeException(
                            "Sản phẩm \"" + pd.getProduct().getName()
                                    + "\" hiện không thể bán!"
                    );
                }

                int quantity = quantities.get(i);

                if (pd.getQuantity() == null
                        || pd.getQuantity() < quantity) {
                    throw new RuntimeException(
                            "Sản phẩm \"" + pd.getProduct().getName()
                                    + "\" chỉ còn "
                                    + (pd.getQuantity() != null
                                    ? pd.getQuantity()
                                    : 0)
                                    + " cái!"
                    );
                }

                subtotal +=
                        (pd.getFinalPrice() != null
                                ? pd.getFinalPrice().doubleValue()
                                : 0)
                                * quantity;
            }

            double discountAmount = 0;
            DiscountCode appliedDiscount = null;

            if (discountCode != null && !discountCode.isBlank()) {

                String validation = discountCodeService.validate(
                        discountCode,
                        subtotal,
                        customer.getId()
                );

                if (!"OK".equals(validation)) {
                    throw new RuntimeException(validation);
                }

                appliedDiscount = discountCodeService.findByCode(discountCode);
                discountAmount = discountCodeService.calculateDiscount(
                        appliedDiscount,
                        subtotal
                );

                if (discountAmount > subtotal) {
                    discountAmount = subtotal;
                }

                bill.setDiscountCode(appliedDiscount);
                bill.setPromotionPrice((float) discountAmount);
            }

            double finalAmount = subtotal - discountAmount;

            bill.setSubtotal((float) subtotal);
            bill.setAmount((float) finalAmount);

            bill = posOrderService.save(bill);

            bill.setCode("HD" + String.format("%05d", bill.getId()));
            bill = posOrderService.save(bill);

            for (int i = 0; i < productDetailIds.size(); i++) {

                ProductDetail pd = productDetailRepository
                        .findById(productDetailIds.get(i))
                        .orElse(null);

                if (pd == null) {
                    throw new RuntimeException("Sản phẩm không tồn tại!");
                }

                BillDetail detail = new BillDetail();
                detail.setBill(bill);
                detail.setProductDetail(pd);
                detail.setMomentPrice(pd.getFinalPrice());
                detail.setQuantity(quantities.get(i));

                posOrderService.addDetail(detail);
            }
            ra.addFlashAttribute("successMsg", "Đã lưu đơn " + bill.getCode() + " vào danh sách chờ!");
            return "redirect:/pos/waiting";
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Lỗi: " + e.getMessage());
            return "redirect:/pos/create";
        }
    }

    // ===== Hủy đơn POS đang chờ =====
    @PostMapping("/cancel/{id}")
    public String cancel(
            @PathVariable Integer id,
            RedirectAttributes ra) {

        try {
            Bill bill = posOrderService.findById(id);

            if (bill == null) {
                throw new RuntimeException("Không tìm thấy đơn POS!");
            }

            posOrderService.cancel(bill);

            ra.addFlashAttribute("success", "Đã hủy đơn POS!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/pos/waiting";
    }
}