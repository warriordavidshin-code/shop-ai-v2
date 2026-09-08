package com.petitcamel.shop.banner.controller;

import com.petitcamel.shop.banner.dto.HeroBannerResponse;
import com.petitcamel.shop.banner.service.HeroBannerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/hero-banners")
public class HeroBannerController {

    private final HeroBannerService heroBannerService;

    public HeroBannerController(HeroBannerService heroBannerService) {
        this.heroBannerService = heroBannerService;
    }

    @GetMapping
    public List<HeroBannerResponse> listActive() {
        return heroBannerService.listActive();
    }
}
