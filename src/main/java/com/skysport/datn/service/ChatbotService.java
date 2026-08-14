package com.skysport.datn.service;

import org.springframework.stereotype.Service;

@Service
public class ChatbotService {

    // Không cần API Key nữa, xử lý trực tiếp bằng logic (Rule-based) để đảm bảo luôn chạy được 100%
    public ChatbotResponse processMessage(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return new ChatbotResponse("Bạn chưa nhập nội dung gì cả.", null);
        }

        String lowerMsg = userMessage.toLowerCase().trim();

        // 1. Phản hồi các câu chào hỏi cơ bản
        if (lowerMsg.matches(".*\\b(chào|hi|hello|helo|xin chào)\\b.*")) {
            return new ChatbotResponse("Chào bạn! Chúc bạn một ngày tốt lành. Bạn đang muốn tìm sản phẩm nào ở SkySport thế? 😊", null);
        }

        if (lowerMsg.contains("cảm ơn") || lowerMsg.contains("thanks") || lowerMsg.contains("tks")) {
            return new ChatbotResponse("Dạ không có gì ạ! SkySport luôn sẵn sàng phục vụ bạn.", null);
        }

        if (lowerMsg.contains("tạm biệt") || lowerMsg.contains("bye")) {
            return new ChatbotResponse("Tạm biệt bạn! Hẹn gặp lại bạn lần sau nhé. 👋", null);
        }

        if (lowerMsg.contains("shop ở đâu") || lowerMsg.contains("địa chỉ")) {
            return new ChatbotResponse("Dạ SkySport có địa chỉ tại cửa hàng chính, bạn có thể xem chi tiết ở phần liên hệ nhé. Bạn đang muốn tìm sản phẩm gì ạ?", null);
        }

        if (lowerMsg.matches(".*\\b(bạn tên gì|mày là ai|ai đây)\\b.*")) {
            return new ChatbotResponse("Mình là trợ lý ảo của SkySport, luôn sẵn sàng giúp bạn tìm kiếm sản phẩm ưng ý nhất! 🤖", null);
        }

        // 2. Nhận diện từ khóa thông minh (Nhãn hàng, Loại sản phẩm)
        String[] brands = {"nike", "puma", "adidas", "lining", "asics", "mizuno", "yonex", "kamito"};
        String[] types = {"áo", "quần", "giày", "dép", "balo", "tất", "phụ kiện", "polo", "thun", "khoác", "sơ mi", "thể thao", "chạy bộ", "bóng đá"};

        StringBuilder extracted = new StringBuilder();
        for (String type : types) {
            if (lowerMsg.matches(".*\\b" + type + "\\b.*")) {
                extracted.append(type).append(" ");
            }
        }
        for (String brand : brands) {
            if (lowerMsg.contains(brand)) {
                extracted.append(brand).append(" ");
            }
        }

        String searchKeyword = extracted.toString().trim();

        // 3. Nếu không tìm thấy từ khóa đặc thù, dùng phương pháp loại bỏ stop-words
        if (searchKeyword.isEmpty()) {
            searchKeyword = lowerMsg
                    .replaceAll("(?U)\\b(tôi|mình|em|anh|chị|đang|muốn|cần|tìm|kiếm|mua|xem|giúp|có|bán|không|nào|được|với|thì|nhé|ạ|cho|shop|ơi|bạn|cái|chiếc|những|các|một|vài|ở|đây|đó|kia|này|thế|như)\\b", "")
                    .replaceAll("[?!.,]", "")
                    .replaceAll("\\s+", " ")
                    .trim();
        }

        if (!searchKeyword.isEmpty() && searchKeyword.length() > 1) {
            return new ChatbotResponse("Dạ, shop có các sản phẩm này có thể phù hợp với yêu cầu của bạn ạ:", searchKeyword);
        }

        // 4. Câu trả lời mặc định nếu không hiểu
        return new ChatbotResponse("Xin lỗi, mình là chatbot nên chưa hiểu ý bạn lắm. Bạn có thể nói rõ tên loại sản phẩm hoặc nhãn hàng (ví dụ: áo nike, giày puma) được không ạ?", null);
    }

    public static class ChatbotResponse {
        private String reply;
        private String searchKeyword;

        public ChatbotResponse(String reply, String searchKeyword) {
            this.reply = reply;
            this.searchKeyword = searchKeyword;
        }

        public String getReply() {
            return reply;
        }

        public String getSearchKeyword() {
            return searchKeyword;
        }
    }
}
