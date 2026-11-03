package com.petitcamel.shop.ai.repository;

import com.petitcamel.shop.ai.domain.PurchaseRecommendation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRecommendationRepository extends JpaRepository<PurchaseRecommendation, Long> {

    Page<PurchaseRecommendation> findByRecommendationTypeOrderByCreatedAtDescIdDesc(String type, Pageable pageable);

    Page<PurchaseRecommendation> findByRecommendationTypeAndStatusOrderByCreatedAtDescIdDesc(String type, String status,
                                                                                         Pageable pageable);
}
