package com.skysport.datn.config;

import com.skysport.datn.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Bắt ConstraintViolationException từ @Validated trên @RequestParam.
 * NOTE: ProfileController đã bỏ @Validated và validate thủ công,
 * nên handler này chủ yếu phục vụ các controller khác nếu dùng @Validated.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public String handleConstraintViolation(ConstraintViolationException ex,
                                            RedirectAttributes redirectAttributes,
                                            HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining(", "));

        String path = request.getServletPath();

        // Trả về đúng trang dựa trên servlet path (đáng tin hơn Referer)
        if (path.contains("change-password")) {
            redirectAttributes.addFlashAttribute("errorPassword", message);
            return "redirect:/profile";
        }
        if (path.contains("update-address")) {
            redirectAttributes.addFlashAttribute("errorAddress", message);
            return "redirect:/profile";
        }
        if (path.contains("profile")) {
            redirectAttributes.addFlashAttribute("error", message);
            return "redirect:/profile";
        }
        if (path.contains("register")) {
            redirectAttributes.addFlashAttribute("error", message);
            return "redirect:/register";
        }
        if (path.contains("checkout")) {
            redirectAttributes.addFlashAttribute("error", message);
            return "redirect:/checkout";
        }

        // Fallback an toàn
        redirectAttributes.addFlashAttribute("error", message);
        return "redirect:/home";
    }

    /**
     * Safety net cho BusinessException KHÔNG được catch cục bộ trong controller.
     * Phần lớn controller (CheckoutController, StaffOrderController, PosOrderController...)
     * đã tự try/catch quanh service call và tự dựng response/redirect riêng — handler này
     * không đụng tới các chỗ đó, chỉ bắt phần còn sót lọt lên tới đây (ví dụ
     * MockVNPayController.checkStatus không có try/catch cục bộ).
     *
     * Phân biệt JSON (AJAX/@ResponseBody) và HTML (form submit) theo header request,
     * vì cùng một BusinessException có thể ném ra từ cả hai loại endpoint.
     */
    @ExceptionHandler(BusinessException.class)
    public Object handleBusinessException(BusinessException ex,
                                          HttpServletRequest request,
                                          RedirectAttributes redirectAttributes) {

        if (isAjaxOrJsonRequest(request)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", ex.getMessage());
            return ResponseEntity.badRequest().body(body);
        }

        redirectAttributes.addFlashAttribute("error", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMsg", ex.getMessage());

        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null && !referer.isBlank() ? referer : "/home");
    }

    private boolean isAjaxOrJsonRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        String contentType = request.getHeader("Content-Type");

        return (accept != null && accept.contains("application/json"))
                || "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (contentType != null && contentType.contains("application/json"));
    }
}