package com.petitcamel.shop.admin.controller;

import com.petitcamel.shop.admin.dto.AdminAiStatsResponse;
import com.petitcamel.shop.admin.service.AdminAiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminAiController {

    private final AdminAiService adminAiService;

    public AdminAiController(AdminAiService adminAiService) {
        this.adminAiService = adminAiService;
    }

    @GetMapping("/ai")
    public AdminAiStatsResponse aiStats() {
        return adminAiService.getStats();
    }
}
