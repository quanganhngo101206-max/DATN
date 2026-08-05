package com.skysport.datn.controller.customer;

import com.skysport.datn.entity.Account;
import com.skysport.datn.entity.Bill;
import com.skysport.datn.entity.Image;
import com.skysport.datn.enums.OrderStatus;
import com.skysport.datn.enums.ReturnRequestStatus;
import com.skysport.datn.repository.ImageRepository;
import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.repository.ProductDetailRepository;
import com.skysport.datn.entity.BillDetail;
import com.skysport.datn.repository.BillDetailRepository;
import com.skysport.datn.repository.BillRepository;
import com.skysport.datn.repository.CustomerRepository;
import com.skysport.datn.entity.ReturnRequest;
import com.skysport.datn.repository.BillReturnRequestRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class CustomerOrderController {

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private BillDetailRepository billDetailRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductDetailRepository productDetailRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private BillReturnRequestRepository billReturnRequestRepository;

    // Danh sách đơn hàng của khách hàng
    @GetMapping("/customer/orders")
    public String listOrders(HttpSession session, Model model) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) {
            return "redirect:/login";
        }

        var customer = customerRepository.findByAccountId(account.getId());
        if (customer == null) {
            model.addAttribute("orders", List.of());
            return "customer/order/list";
        }

        List<Bill> orders = billRepository.findByCustomerId(customer.getId());
        model.addAttribute("orders", orders != null ? orders : List.of());
        model.addAttribute("productImageMap", buildImageMap(orders != null ? orders : List.of()));
        return "customer/order/list";
    }

    // Chi tiết đơn hàng
    @GetMapping("/customer/order/detail/{id}")
    public String orderDetail(@PathVariable Integer id, HttpSession session, Model model) {
        Account account = (Account) session.getAttribute("account");
        if (account == null) {
            return "redirect:/login";
        }

        Bill bill = billRepository.findById(id).orElse(null);
        if (bill == null) {
            return "redirect:/customer/orders";
        }

        var customer = customerRepository.findByAccountId(account.getId());
        if (customer == null || !bill.getCustomer().getId().equals(customer.getId())) {
            return "redirect:/customer/orders";
        }

        List<BillDetail> details = billDetailRepository.findByBillId(id);

        // Build productImageMap từ details của đơn hàng này
        Map<Integer, String> productImageMap = new HashMap<>();
        for (BillDetail detail : details) {
            if (detail.getProductDetail() != null && detail.getProductDetail().getProduct() != null) {
                Integer productId = detail.getProductDetail().getProduct().getId();
                if (!productImageMap.containsKey(productId)) {
                    List<Image> images = imageRepository.findByProductId(productId);
                    productImageMap.put(productId, images.isEmpty() ? null : images.get(0).getLink());
                }
            }
        }

        model.addAttribute("bill", bill);
        model.addAttribute("details", details);
        model.addAttribute("productImageMap", productImageMap);

        // Kiểm tra đơn hàng đã có yêu cầu trả hàng đang xử lý/đã duyệt chưa
        List<ReturnRequest> returnRequests = billReturnRequestRepository.findByBill_Id(id);
        boolean hasActiveReturn = returnRequests.stream().anyMatch(r -> ReturnRequestStatus.PENDING.matches(r.getStatus()) || ReturnRequestStatus.APPROVED.matches(r.getStatus()));
        model.addAttribute("hasActiveReturn", hasActiveReturn);

        return "customer/order/detail";
    }

    // Hủy đơn hàng (AJAX)
    @Transactional
    @PostMapping("/customer/order/cancel/{id}")
    @ResponseBody
    public Map<String, Object> cancelOrder(@PathVariable Integer id, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Account account = (Account) session.getAttribute("account");

        if (account == null) {
            result.put("success", false);
            result.put("message", "Vui lòng đăng nhập!");
            return result;
        }

        Bill bill = billRepository.findById(id).orElse(null);
        if (bill == null) {
            result.put("success", false);
            result.put("message", "Không tìm thấy đơn hàng!");
            return result;
        }

        var customer = customerRepository.findByAccountId(account.getId());
        if (customer == null || !bill.getCustomer().getId().equals(customer.getId())) {
            result.put("success", false);
            result.put("message", "Bạn không có quyền hủy đơn hàng này!");
            return result;
        }

        if (!OrderStatus.PENDING.matches(bill.getStatus())) {
            result.put("success", false);
            result.put("message", "Không thể hủy đơn hàng ở trạng thái hiện tại!");
            return result;
        }

        bill.setStatus(OrderStatus.CANCELLED.getValue());
        billRepository.save(bill);

        List<BillDetail> details = billDetailRepository.findByBillId(bill.getId());
        for (BillDetail detail : details) {
            if (detail.getProductDetail() != null) {
                ProductDetail pd = productDetailRepository.findById(detail.getProductDetail().getId()).orElse(null);
                if (pd != null) {
                    pd.setQuantity((pd.getQuantity() != null ? pd.getQuantity() : 0) + detail.getQuantity());
                    productDetailRepository.save(pd);
                }
            }
        }

        result.put("success", true);
        result.put("message", "Đã hủy đơn hàng thành công!");
        return result;
    }

    private Map<Integer, String> buildImageMap(List<Bill> orders) {
        Map<Integer, String> map = new HashMap<>();
        for (Bill order : orders) {
            if (order.getBillDetails() == null) continue;
            for (BillDetail detail : order.getBillDetails()) {
                if (detail.getProductDetail() != null && detail.getProductDetail().getProduct() != null) {
                    Integer productId = detail.getProductDetail().getProduct().getId();
                    if (!map.containsKey(productId)) {
                        List<Image> images = imageRepository.findByProductId(productId);
                        map.put(productId, images.isEmpty() ? null : images.get(0).getLink());
                    }
                }
            }
        }
        return map;
    }
}