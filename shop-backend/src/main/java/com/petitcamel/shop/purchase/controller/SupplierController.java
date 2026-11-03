package com.petitcamel.shop.purchase.controller;

import com.petitcamel.shop.purchase.service.SupplierService;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public List<SupplierService.SupplierResponse> list() {
        return supplierService.list();
    }

    @GetMapping("/{id}")
    public SupplierService.SupplierResponse get(@PathVariable Long id) {
        return supplierService.get(id);
    }

    @PostMapping
    public SupplierService.SupplierResponse create(@AuthenticationPrincipal MemberPrincipal principal,
                                                   @Valid @RequestBody SupplierService.SupplierRequest request) {
        return supplierService.create(request, principal.getMemberId());
    }

    @PutMapping("/{id}")
    public SupplierService.SupplierResponse update(@AuthenticationPrincipal MemberPrincipal principal,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody SupplierService.SupplierRequest request) {
        return supplierService.update(id, request, principal.getMemberId());
    }

    @GetMapping("/{id}/mappings")
    public List<SupplierService.MappingResponse> mappings(@PathVariable Long id) {
        return supplierService.mappings(id);
    }

    @DeleteMapping("/{id}/mappings/{mappingId}")
    public ResponseEntity<Void> deleteMapping(@AuthenticationPrincipal MemberPrincipal principal,
                                              @PathVariable Long id, @PathVariable Long mappingId) {
        supplierService.deleteMapping(id, mappingId, principal.getMemberId());
        return ResponseEntity.noContent().build();
    }
}
