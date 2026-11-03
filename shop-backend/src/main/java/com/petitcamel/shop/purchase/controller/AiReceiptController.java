package com.petitcamel.shop.purchase.controller;

import com.petitcamel.shop.purchase.service.AiReceiptService;
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
@RequestMapping("/api/admin/purchases/ai")
public class AiReceiptController {

    private final AiReceiptService aiReceiptService;

    public AiReceiptController(AiReceiptService aiReceiptService) {
        this.aiReceiptService = aiReceiptService;
    }

    /** Returns a draft for review; the ledger entry is only created when the admin saves it. */
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AiReceiptService.ReceiptAnalysisResponse analyze(@AuthenticationPrincipal MemberPrincipal principal,
                                                            @RequestPart("files") List<MultipartFile> files,
                                                            @RequestParam(defaultValue = "false") boolean forceRefresh) {
        return aiReceiptService.analyze(files, forceRefresh, principal.getMemberId());
    }
}
