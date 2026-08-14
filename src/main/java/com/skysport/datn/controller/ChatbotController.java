package com.skysport.datn.controller;

import com.skysport.datn.entity.Image;
import com.skysport.datn.entity.Product;
import com.skysport.datn.entity.ProductDetail;
import com.skysport.datn.repository.ImageRepository;
import com.skysport.datn.repository.ProductDetailRepository;
import com.skysport.datn.service.ChatbotService;
import com.skysport.datn.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    @Autowired
    private ProductService productService;
    
    @Autowired
    private ChatbotService chatbotService;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private ProductDetailRepository productDetailRepository;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.getOrDefault("message", "").trim();
        Map<String, Object> response = new HashMap<>();

        if (userMessage.isEmpty()) {
            response.put("reply", "Bạn chưa nhập tin nhắn nào.");
            return ResponseEntity.ok(response);
        }

        // Gọi AI Service để phân tích và lấy câu trả lời
        ChatbotService.ChatbotResponse aiResponse = chatbotService.processMessage(userMessage);
        
        response.put("reply", aiResponse.getReply());

        // Nếu AI phát hiện người dùng muốn tìm hàng, tiến hành query Database
        if (aiResponse.getSearchKeyword() != null && !aiResponse.getSearchKeyword().isEmpty()) {
            // Tìm kiếm sản phẩm (tăng lên 5 sản phẩm)
            Page<Product> products = productService.search(aiResponse.getSearchKeyword(), null, null, null, null, null, 1, PageRequest.of(0, 5));
            // Nếu không tìm thấy cả cụm từ (ví dụ "áo adidas"), thử tìm bằng từ dài nhất (ví dụ "adidas")
            if ((products == null || products.isEmpty()) && aiResponse.getSearchKeyword().contains(" ")) {
                String[] words = aiResponse.getSearchKeyword().split(" ");
                String bestWord = "";
                for (String w : words) {
                    if (w.length() > bestWord.length()) {
                        bestWord = w;
                    }
                }
                if (!bestWord.isEmpty()) {
                    products = productService.search(bestWord, null, null, null, null, null, 1, PageRequest.of(0, 5));                }
            }

            if (products != null && !products.isEmpty()) {
                response.put("products", mapProducts(products));
                // Nếu AI chưa có câu nói kèm sản phẩm, chèn thêm 1 câu
                if (aiResponse.getReply() == null || aiResponse.getReply().trim().isEmpty()) {
                     response.put("reply", "Dạ shop có các sản phẩm này phù hợp với bạn ạ:");
                }
            } else {
                // FALLBACK: Nếu không tìm thấy, lấy 5 sản phẩm bất kỳ để gợi ý
                Page<Product> defaultProducts = productService.search("", null, null, null, null, null, 1, PageRequest.of(0, 5));
                if (defaultProducts != null && !defaultProducts.isEmpty()) {
                    response.put("products", mapProducts(defaultProducts));
//                    response.put("reply", aiResponse.getReply() + "\n\n(Dạ rất tiếc shop không tìm thấy chính xác '" + aiResponse.getSearchKeyword() + "', nhưng bạn có thể tham khảo các mẫu hot này nhé:)");
                } else {
                    response.put("reply", aiResponse.getReply() + "\n\n(Rất tiếc hiện tại shop chưa tìm thấy sản phẩm nào khớp với yêu cầu của bạn, bạn thử từ khóa khác nhé!)");
                }
            }
        }

        return ResponseEntity.ok(response);
    }

    private List<Map<String, Object>> mapProducts(Page<Product> products) {
        List<Map<String, Object>> productList = new ArrayList<>();
        for (Product p : products) {
            Map<String, Object> pInfo = new HashMap<>();
            pInfo.put("id", p.getId());
            pInfo.put("name", p.getName());
            
            // Lấy hình ảnh đầu tiên
            List<Image> images = imageRepository.findByProductId(p.getId());
            if (images != null && !images.isEmpty()) {
                pInfo.put("image", images.get(0).getLink());
            } else {
                pInfo.put("image", "/assets/images/default-product.jpg");
            }
            
            // Lấy giá
            List<ProductDetail> details = productDetailRepository.findByProductId(p.getId());
            if (details != null && !details.isEmpty()) {
                Float price = details.get(0).getFinalPrice();
                if (price != null) {
                    pInfo.put("price", String.format("%,.0f VNĐ", price));
                } else {
                    pInfo.put("price", "Liên hệ");
                }
            } else {
                pInfo.put("price", "Liên hệ");
            }

            productList.add(pInfo);
        }
        return productList;
    }
}
