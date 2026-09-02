package com.skysport.datn.controller;

import com.skysport.datn.entity.DiscountCode;
import com.skysport.datn.service.DiscountCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

//@Controller
@RequestMapping("/admin/discount")
public class DiscountCodeController {

    @Autowired
    private DiscountCodeService discountCodeService;

    @GetMapping
    public String list(Model model) {
        try {
            System.out.println("=== Ã„Âang gÃ¡Â»Âi /admin/discount ===");
            List<DiscountCode> discounts = discountCodeService.findAll();
            System.out.println("SÃ¡Â»â€˜ lÃ†Â°Ã¡Â»Â£ng mÃƒÂ£ giÃ¡ÂºÂ£m giÃƒÂ¡: " + (discounts != null ? discounts.size() : 0));

            model.addAttribute("discounts", discounts != null ? discounts : List.of());
            model.addAttribute("discount", new DiscountCode());
            return "admin/discount/list";
        } catch (Exception e) {
            System.err.println("LÃ¡Â»â€”i trong controller: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "CÃƒÂ³ lÃ¡Â»â€”i xÃ¡ÂºÂ£y ra: " + e.getMessage());
            model.addAttribute("discounts", List.of());
            model.addAttribute("discount", new DiscountCode());
            return "admin/discount/list";
        }
    }

    @PostMapping("/save")
    public String save(@ModelAttribute DiscountCode discountCode) {
        try {
            discountCodeService.save(discountCode);
        } catch (Exception e) {
            System.err.println("LÃ¡Â»â€”i khi lÃ†Â°u: " + e.getMessage());
        }
        return "redirect:/admin/discount";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Integer id, Model model) {
        try {
            model.addAttribute("discount", discountCodeService.findById(id));
            model.addAttribute("discounts", discountCodeService.findAll());
        } catch (Exception e) {
            System.err.println("LÃ¡Â»â€”i khi sÃ¡Â»Â­a: " + e.getMessage());
            model.addAttribute("discount", new DiscountCode());
            model.addAttribute("discounts", List.of());
        }
        return "admin/discount/list";
    }

    @PostMapping("/update")
    public String update(@ModelAttribute DiscountCode discountCode) {
        try {
            DiscountCode old = discountCodeService.findById(discountCode.getId());
            if (old != null) {
                old.setCode(discountCode.getCode());
                old.setDetail(discountCode.getDetail());
                old.setType(discountCode.getType());
                old.setDiscountAmount(discountCode.getDiscountAmount());
                old.setPercentage(discountCode.getPercentage());
                old.setMinimumAmountInCart(discountCode.getMinimumAmountInCart());
                old.setMaximumAmount(discountCode.getMaximumAmount());
                old.setMaximumUsage(discountCode.getMaximumUsage());
                old.setStartDate(discountCode.getStartDate());
                old.setEndDate(discountCode.getEndDate());
                old.setStatus(discountCode.getStatus());
                discountCodeService.update(old);
            }
        } catch (Exception e) {
            System.err.println("LÃ¡Â»â€”i khi cÃ¡ÂºÂ­p nhÃ¡ÂºÂ­t: " + e.getMessage());
        }
        return "redirect:/admin/discount";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Integer id) {
        try {
            discountCodeService.delete(id);
        } catch (Exception e) {
            System.err.println("LÃ¡Â»â€”i khi xÃƒÂ³a: " + e.getMessage());
        }
        return "redirect:/admin/discount";
    }
}
