package com.petitcamel.shop.banner.repository;

import com.petitcamel.shop.banner.domain.HeroBanner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HeroBannerRepository extends JpaRepository<HeroBanner, Long> {

    List<HeroBanner> findByActiveTrueOrderBySortOrderAscBannerIdAsc();

    List<HeroBanner> findAllByOrderBySortOrderAscBannerIdAsc();
}
