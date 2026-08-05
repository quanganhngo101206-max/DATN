package com.skysport.datn.config;

import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
                                            jakarta.servlet.http.HttpServletRequest request) {
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
}