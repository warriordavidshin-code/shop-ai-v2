package com.petitcamel.shop.recommendation.repository;

import com.petitcamel.shop.recommendation.domain.RecommendationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RecommendationEventRepository extends JpaRepository<RecommendationEvent, Long> {

    @Query("SELECT e.eventType, COUNT(e) FROM RecommendationEvent e GROUP BY e.eventType")
    List<Object[]> countGroupedByEventType();
}
