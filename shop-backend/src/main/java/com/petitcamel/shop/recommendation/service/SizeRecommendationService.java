package com.petitcamel.shop.recommendation.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.product.domain.ProductMeasurement;
import com.petitcamel.shop.product.repository.ProductMeasurementRepository;
import com.petitcamel.shop.product.repository.ProductRepository;
import com.petitcamel.shop.recommendation.dto.SizeRecommendationRequest;
import com.petitcamel.shop.recommendation.dto.SizeRecommendationResponse;
import com.petitcamel.shop.recommendation.dto.SizeRecommendationResponse.Confidence;
import com.petitcamel.shop.review.domain.FitRating;
import com.petitcamel.shop.review.domain.Review;
import com.petitcamel.shop.review.domain.ReviewStatus;
import com.petitcamel.shop.review.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class SizeRecommendationService {

    private final ProductRepository productRepository;
    private final ProductMeasurementRepository productMeasurementRepository;
    private final ReviewRepository reviewRepository;

    public SizeRecommendationService(
            ProductRepository productRepository,
            ProductMeasurementRepository productMeasurementRepository,
            ReviewRepository reviewRepository) {
        this.productRepository = productRepository;
        this.productMeasurementRepository = productMeasurementRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public SizeRecommendationResponse recommend(SizeRecommendationRequest request) {
        if (!productRepository.existsById(request.productId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }

        List<ProductMeasurement> measurements =
                productMeasurementRepository.findByProductIdOrderBySizeAsc(request.productId());
        List<String> reasons = new ArrayList<>();

        if (measurements.isEmpty()) {
            reasons.add("상품 실측 데이터가 없어 사이즈를 확신할 수 없습니다.");
            return uncertain(request.productId(), reasons);
        }

        boolean hasBody = request.heightCm() != null || request.weightKg() != null;
        List<Review> reviews = reviewRepository.findByProductIdAndStatus(
                request.productId(), ReviewStatus.VISIBLE);
        FitStats fitStats = FitStats.from(reviews);

        if (!hasBody && fitStats.sampleCount() == 0) {
            reasons.add("키·몸무게 또는 리뷰 핏 정보가 부족합니다.");
            return uncertain(request.productId(), reasons);
        }

        if (measurements.size() == 1) {
            String only = measurements.get(0).getSize();
            reasons.add("등록된 사이즈가 " + only + " 하나뿐입니다.");
            Confidence confidence = hasBody ? Confidence.MEDIUM : Confidence.LOW;
            return new SizeRecommendationResponse(request.productId(), only, confidence, reasons);
        }

        int baseIndex = selectBaseIndex(measurements, request.heightCm(), request.weightKg(), reasons);
        int adjusted = adjustForPreferredFit(baseIndex, request.preferredFit(), measurements.size(), reasons);
        adjusted = adjustForReviewFit(adjusted, fitStats, measurements.size(), reasons);

        String recommended = measurements.get(adjusted).getSize();
        Confidence confidence = resolveConfidence(hasBody, fitStats.sampleCount(), request.preferredFit());
        reasons.add("추천 사이즈는 상품에 등록된 실측 사이즈만 사용합니다.");

        return new SizeRecommendationResponse(request.productId(), recommended, confidence, List.copyOf(reasons));
    }

    private int selectBaseIndex(
            List<ProductMeasurement> measurements,
            Integer heightCm,
            Integer weightKg,
            List<String> reasons) {
        double targetChest = estimateChestCm(heightCm, weightKg);
        double targetWaist = estimateWaistCm(heightCm, weightKg);

        Optional<Integer> bestByMeasure = findClosestByBody(measurements, targetChest, targetWaist);
        if (bestByMeasure.isPresent()) {
            reasons.add(String.format(
                    Locale.ROOT,
                    "추정 가슴둘레 %.0fcm / 허리 %.0fcm 기준으로 실측과 비교했습니다.",
                    targetChest,
                    targetWaist));
            return bestByMeasure.get();
        }

        int band = sizeBandFromBody(heightCm, weightKg);
        int index = Math.min(Math.max(band, 0), measurements.size() - 1);
        reasons.add("키·몸무게 구간으로 기본 사이즈를 추정했습니다.");
        return index;
    }

    private Optional<Integer> findClosestByBody(
            List<ProductMeasurement> measurements,
            double targetChest,
            double targetWaist) {
        int bestIdx = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < measurements.size(); i++) {
            ProductMeasurement m = measurements.get(i);
            Double chest = toDouble(m.getChest());
            Double waist = toDouble(m.getWaist());
            if (chest == null && waist == null) {
                continue;
            }
            double score = 0;
            int parts = 0;
            if (chest != null) {
                score += Math.abs(chest - targetChest);
                parts++;
            }
            if (waist != null) {
                score += Math.abs(waist - targetWaist);
                parts++;
            }
            score = score / parts;
            if (score < bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }
        return bestIdx >= 0 ? Optional.of(bestIdx) : Optional.empty();
    }

    private int adjustForPreferredFit(int index, String preferredFit, int sizeCount, List<String> reasons) {
        String fit = preferredFit == null ? "" : preferredFit.trim().toUpperCase(Locale.ROOT);
        if (fit.contains("RELAX") || fit.contains("LOOSE") || fit.contains("OVER")) {
            if (index < sizeCount - 1) {
                reasons.add("여유 핏 선호로 한 단계 큰 사이즈를 반영했습니다.");
                return index + 1;
            }
        }
        if (fit.contains("SLIM") || fit.contains("TIGHT") || fit.contains("FITTED")) {
            if (index > 0) {
                reasons.add("슬림 핏 선호로 한 단계 작은 사이즈를 반영했습니다.");
                return index - 1;
            }
        }
        if (!fit.isBlank()) {
            reasons.add("선호 핏(" + preferredFit + ")을 참고했습니다.");
        }
        return index;
    }

    private int adjustForReviewFit(int index, FitStats stats, int sizeCount, List<String> reasons) {
        if (stats.sampleCount() < 3) {
            return index;
        }
        if (stats.largeRatio() >= 0.5 && index > 0) {
            reasons.add("리뷰에서 크다는 피드백이 많아 한 단계 작게 조정했습니다.");
            return index - 1;
        }
        if (stats.smallRatio() >= 0.5 && index < sizeCount - 1) {
            reasons.add("리뷰에서 작다는 피드백이 많아 한 단계 크게 조정했습니다.");
            return index + 1;
        }
        if (stats.trueToSizeRatio() >= 0.5) {
            reasons.add("리뷰 다수가 정사이즈라고 응답했습니다.");
        }
        return index;
    }

    private Confidence resolveConfidence(boolean hasBody, int reviewSamples, String preferredFit) {
        int score = 0;
        if (hasBody) {
            score += 2;
        }
        if (reviewSamples >= 5) {
            score += 2;
        } else if (reviewSamples >= 3) {
            score += 1;
        }
        if (preferredFit != null && !preferredFit.isBlank()) {
            score += 1;
        }
        if (score >= 4) {
            return Confidence.HIGH;
        }
        if (score >= 2) {
            return Confidence.MEDIUM;
        }
        if (score >= 1) {
            return Confidence.LOW;
        }
        return Confidence.UNCERTAIN;
    }

    private SizeRecommendationResponse uncertain(Long productId, List<String> reasons) {
        return new SizeRecommendationResponse(productId, null, Confidence.UNCERTAIN, List.copyOf(reasons));
    }

    /**
     * Rough half-chest estimate from height/weight when detailed tape measures are unavailable.
     */
    static double estimateChestCm(Integer heightCm, Integer weightKg) {
        double h = heightCm != null ? heightCm : 165;
        double w = weightKg != null ? weightKg : 55;
        return 0.25 * h + 0.35 * w + 8;
    }

    static double estimateWaistCm(Integer heightCm, Integer weightKg) {
        double h = heightCm != null ? heightCm : 165;
        double w = weightKg != null ? weightKg : 55;
        return 0.18 * h + 0.40 * w;
    }

    static int sizeBandFromBody(Integer heightCm, Integer weightKg) {
        int h = heightCm != null ? heightCm : 165;
        int w = weightKg != null ? weightKg : 55;
        int score = (h - 155) / 8 + (w - 45) / 8;
        return Math.max(0, score);
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private record FitStats(int sampleCount, double smallRatio, double trueToSizeRatio, double largeRatio) {
        static FitStats from(List<Review> reviews) {
            List<FitRating> ratings = reviews.stream()
                    .map(Review::getFitRating)
                    .filter(r -> r != null)
                    .toList();
            int n = ratings.size();
            if (n == 0) {
                return new FitStats(0, 0, 0, 0);
            }
            long small = ratings.stream().filter(r -> r == FitRating.SMALL).count();
            long tts = ratings.stream().filter(r -> r == FitRating.TRUE_TO_SIZE).count();
            long large = ratings.stream().filter(r -> r == FitRating.LARGE).count();
            return new FitStats(n, (double) small / n, (double) tts / n, (double) large / n);
        }
    }
}
