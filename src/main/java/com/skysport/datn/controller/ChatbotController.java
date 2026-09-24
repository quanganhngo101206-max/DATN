package com.skysport.datn.controller;

import com.skysport.datn.service.ChatbotService;
import com.skysport.datn.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chatbot API — tìm sản phẩm gợi ý cho người dùng.
 *
 * Phase 2 fix: N+1 query đã được loại bỏ.
 * Trước: mỗi Product gọi thêm imageRepository.findByProductId() + productDetailRepository.findByProductId()
 * → 1 + 5 + 5 = 11 query cho 5 sản phẩm.
 *
 * Sau: ProductService.searchForChatbot() dùng DTO projection, 1 query duy nhất trả
 * [id, name, image, price] thông qua JOIN FETCH hoặc subquery.
 */
@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ProductService productService;
    private final ChatbotService chatbotService;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.getOrDefault("message", "").trim();
        Map<String, Object> response = new HashMap<>();

        if (userMessage.isEmpty()) {
            response.put("reply", "Bạn chưa nhập tin nhắn nào.");
            return ResponseEntity.ok(response);
        }

        ChatbotService.ChatbotResponse aiResponse = chatbotService.processMessage(userMessage);
        response.put("reply", aiResponse.getReply());

        if (aiResponse.getSearchKeyword() != null && !aiResponse.getSearchKeyword().isEmpty()) {
            List<Map<String, Object>> productList = searchProducts(aiResponse.getSearchKeyword());

            if (!productList.isEmpty()) {
                response.put("products", productList);
                if (aiResponse.getReply() == null || aiResponse.getReply().trim().isEmpty()) {
                    response.put("reply", "Dạ shop có các sản phẩm này phù hợp với bạn ạ:");
                }
            } else {
                // Fallback: gợi ý sản phẩm mặc định
                List<Map<String, Object>> defaultProducts = searchProducts("");
                if (!defaultProducts.isEmpty()) {
                    response.put("products", defaultProducts);
                } else {
                    response.put("reply", aiResponse.getReply()
                            + "\n\n(Rất tiếc hiện tại shop chưa tìm thấy sản phẩm nào khớp với yêu cầu của bạn, bạn thử từ khóa khác nhé!)");
                }
            }
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Tìm sản phẩm và map sang DTO — 1 query duy nhất thay vì N+1.
     * ProductService.searchForChatbot() trả List<ProductChatbotDto> qua JOIN/subquery trong DB.
     * Nếu không tìm được cả cụm từ, thử từ dài nhất trong keyword.
     */
    private List<Map<String, Object>> searchProducts(String keyword) {
        // Tìm theo keyword đầy đủ
        List<ProductService.ProductChatbotDto> dtos =
                productService.searchForChatbot(keyword, 5);

        // Fallback: thử từ dài nhất nếu keyword nhiều từ và không tìm thấy
        if (dtos.isEmpty() && keyword.contains(" ")) {
            String bestWord = "";
            for (String w : keyword.split(" ")) {
                if (w.length() > bestWord.length()) bestWord = w;
            }
            if (!bestWord.isEmpty()) {
                dtos = productService.searchForChatbot(bestWord, 5);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(dtos.size());
        for (ProductService.ProductChatbotDto dto : dtos) {
            Map<String, Object> m = new HashMap<>();
            m.put("id",    dto.getId());
            m.put("name",  dto.getName());
            m.put("image", dto.getImage() != null ? dto.getImage() : "/assets/images/default-product.jpg");
            m.put("price", dto.getPrice() != null
                    ? String.format("%,.0f VNĐ", dto.getPrice()) : "Liên hệ");
            result.add(m);
        }
        return result;
    }
}