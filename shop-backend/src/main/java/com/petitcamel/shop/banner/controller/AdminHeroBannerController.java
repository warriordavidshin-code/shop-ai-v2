package com.petitcamel.shop.banner.controller;

import com.petitcamel.shop.banner.dto.HeroBannerRequest;
import com.petitcamel.shop.banner.dto.HeroBannerResponse;
import com.petitcamel.shop.banner.service.HeroBannerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/hero-banners")
public class AdminHeroBannerController {

    private final HeroBannerService heroBannerService;

    public AdminHeroBannerController(HeroBannerService heroBannerService) {
        this.heroBannerService = heroBannerService;
    }

    @GetMapping
    public List<HeroBannerResponse> list() {
        return heroBannerService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HeroBannerResponse create(@Valid @RequestBody HeroBannerRequest request) {
        return heroBannerService.create(request);
    }

    @PutMapping("/{bannerId}")
    public HeroBannerResponse update(
            @PathVariable Long bannerId,
            @Valid @RequestBody HeroBannerRequest request) {
        return heroBannerService.update(bannerId, request);
    }

    @DeleteMapping("/{bannerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long bannerId) {
        heroBannerService.delete(bannerId);
    }
}
