package com.petitcamel.shop.product.controller;

import com.petitcamel.shop.product.service.AiProductService;
import com.petitcamel.shop.security.MemberPrincipal;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/products/ai")
public class AiProductController {

    private final AiProductService aiProductService;

    public AiProductController(AiProductService aiProductService) {
        this.aiProductService = aiProductService;
    }

    /** Photo → product draft. Images come as uploads and/or already-uploaded {@code /uploads/...} URLs. */
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AiProductService.ProductAnalysisResponse analyze(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "imageUrls", required = false) List<String> imageUrls,
            @RequestParam(value = "hint", required = false) String hint,
            @RequestParam(defaultValue = "false") boolean forceRefresh) {
        return aiProductService.analyze(files, imageUrls, hint, forceRefresh, principal.getMemberId());
    }

    /** Purchase statement line (+ optional photos) → product draft with recommended price. */
    @PostMapping(value = "/prepare", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE,
            MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public AiProductService.PrepareResponse prepare(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam("ledgerItemId") Long ledgerItemId,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "imageUrls", required = false) List<String> imageUrls,
            @RequestParam(defaultValue = "false") boolean forceRefresh) {
        return aiProductService.prepare(ledgerItemId, files, imageUrls, forceRefresh, principal.getMemberId());
    }
}
