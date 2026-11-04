package com.petitcamel.shop.ai.repository;

import com.petitcamel.shop.ai.domain.AiAnalysisHistory;
import com.petitcamel.shop.ai.domain.AiAnalysisStatus;
import com.petitcamel.shop.ai.domain.AiAnalysisType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AiAnalysisHistoryRepository extends JpaRepository<AiAnalysisHistory, Long> {

    Optional<AiAnalysisHistory> findFirstByAnalysisTypeAndInputHashAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            AiAnalysisType analysisType, String inputHash, AiAnalysisStatus status, Instant after);

    @Query("""
            select count(h) as calls,
                   coalesce(sum(h.inputTokens), 0) as inputTokens,
                   coalesce(sum(h.outputTokens), 0) as outputTokens,
                   coalesce(sum(h.estimatedCostUsd), 0) as estimatedCostUsd
            from AiAnalysisHistory h
            where h.createdAt >= :from and h.provider in (com.petitcamel.shop.ai.domain.AiProviderType.OPENAI,
                                                          com.petitcamel.shop.ai.domain.AiProviderType.GEMINI)
            """)
    UsageSummary usageSince(@Param("from") Instant from);

    interface UsageSummary {
        Number getCalls();

        Number getInputTokens();

        Number getOutputTokens();

        Number getEstimatedCostUsd();
    }

    long countByCreatedAtAfterAndStatusAndProvider(Instant after, AiAnalysisStatus status,
            com.petitcamel.shop.ai.domain.AiProviderType provider);

    org.springframework.data.domain.Page<AiAnalysisHistory> findAllByOrderByCreatedAtDescIdDesc(
            org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<AiAnalysisHistory> findByAnalysisTypeOrderByCreatedAtDescIdDesc(
            AiAnalysisType type, org.springframework.data.domain.Pageable pageable);
}
