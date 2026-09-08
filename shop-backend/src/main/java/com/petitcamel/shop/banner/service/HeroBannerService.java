package com.petitcamel.shop.banner.service;

import com.petitcamel.shop.banner.domain.HeroBanner;
import com.petitcamel.shop.banner.dto.HeroBannerRequest;
import com.petitcamel.shop.banner.dto.HeroBannerResponse;
import com.petitcamel.shop.banner.repository.HeroBannerRepository;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class HeroBannerService {

    private final HeroBannerRepository heroBannerRepository;
    private final Clock clock;

    public HeroBannerService(HeroBannerRepository heroBannerRepository, Clock clock) {
        this.heroBannerRepository = heroBannerRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<HeroBannerResponse> listActive() {
        return heroBannerRepository.findByActiveTrueOrderBySortOrderAscBannerIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HeroBannerResponse> listAll() {
        return heroBannerRepository.findAllByOrderBySortOrderAscBannerIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public HeroBannerResponse create(HeroBannerRequest request) {
        Instant now = clock.instant();
        HeroBanner banner = new HeroBanner();
        apply(banner, request);
        banner.setCreatedAt(now);
        banner.setUpdatedAt(now);
        return toResponse(heroBannerRepository.save(banner));
    }

    @Transactional
    public HeroBannerResponse update(Long bannerId, HeroBannerRequest request) {
        HeroBanner banner = require(bannerId);
        apply(banner, request);
        banner.setUpdatedAt(clock.instant());
        return toResponse(heroBannerRepository.save(banner));
    }

    @Transactional
    public void delete(Long bannerId) {
        HeroBanner banner = require(bannerId);
        heroBannerRepository.delete(banner);
    }

    private void apply(HeroBanner banner, HeroBannerRequest request) {
        banner.setImageUrl(request.imageUrl().trim());
        banner.setOverlayText(request.overlayText() == null ? null : request.overlayText().trim());
        banner.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        banner.setActive(Boolean.TRUE.equals(request.active()));
    }

    private HeroBanner require(Long bannerId) {
        return heroBannerRepository.findById(bannerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "배너를 찾을 수 없습니다."));
    }

    private HeroBannerResponse toResponse(HeroBanner banner) {
        return new HeroBannerResponse(
                banner.getBannerId(),
                banner.getImageUrl(),
                banner.getOverlayText(),
                banner.getSortOrder(),
                banner.getActive(),
                banner.getCreatedAt(),
                banner.getUpdatedAt());
    }
}
