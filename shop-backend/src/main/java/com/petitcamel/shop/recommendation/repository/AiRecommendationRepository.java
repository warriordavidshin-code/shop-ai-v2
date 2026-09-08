package com.petitcamel.shop.recommendation.repository;

import com.petitcamel.shop.recommendation.domain.AiRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AiRecommendationRepository extends JpaRepository<AiRecommendation, Long> {

    List<AiRecommendation> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    @Query("SELECT r.provider, COUNT(r) FROM AiRecommendation r GROUP BY r.provider")
    List<Object[]> countGroupedByProvider();

    @Query("SELECT r.recommendationType, COUNT(r) FROM AiRecommendation r GROUP BY r.recommendationType")
    List<Object[]> countGroupedByType();
}
