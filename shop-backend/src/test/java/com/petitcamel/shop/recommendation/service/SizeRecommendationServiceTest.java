package com.petitcamel.shop.recommendation.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.product.domain.ProductMeasurement;
import com.petitcamel.shop.product.repository.ProductMeasurementRepository;
import com.petitcamel.shop.product.repository.ProductRepository;
import com.petitcamel.shop.recommendation.dto.SizeRecommendationRequest;
import com.petitcamel.shop.recommendation.dto.SizeRecommendationResponse;
import com.petitcamel.shop.recommendation.dto.SizeRecommendationResponse.Confidence;
import com.petitcamel.shop.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SizeRecommendationServiceTest {

    @Mock
    ProductRepository productRepository;

    @Mock
    ProductMeasurementRepository productMeasurementRepository;

    @Mock
    ReviewRepository reviewRepository;

    SizeRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new SizeRecommendationService(
                productRepository, productMeasurementRepository, reviewRepository);
    }

    @Test
    void returnsUncertainWhenNoMeasurements() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(productMeasurementRepository.findByProductIdOrderBySizeAsc(1L)).thenReturn(List.of());

        SizeRecommendationResponse response = service.recommend(
                new SizeRecommendationRequest(1L, 165, 55, "REGULAR"));

        assertThat(response.confidence()).isEqualTo(Confidence.UNCERTAIN);
        assertThat(response.recommendedSize()).isNull();
        assertThat(response.reasons()).isNotEmpty();
    }

    @Test
    void recommendsExistingMeasurementSizeOnly() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(productMeasurementRepository.findByProductIdOrderBySizeAsc(1L)).thenReturn(List.of(
                measurement("S", "46.0", "44.0"),
                measurement("M", "48.0", "46.0"),
                measurement("L", "50.0", "48.0")));
        when(reviewRepository.findByProductIdAndStatus(1L, com.petitcamel.shop.review.domain.ReviewStatus.VISIBLE))
                .thenReturn(List.of());

        SizeRecommendationResponse response = service.recommend(
                new SizeRecommendationRequest(1L, 165, 55, "REGULAR"));

        assertThat(response.recommendedSize()).isIn("S", "M", "L");
        assertThat(response.confidence()).isNotEqualTo(Confidence.UNCERTAIN);
        assertThat(List.of("XS", "XL", "FREE")).doesNotContain(response.recommendedSize());
    }

    @Test
    void returnsUncertainWhenBodyAndReviewsMissing() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(productMeasurementRepository.findByProductIdOrderBySizeAsc(1L)).thenReturn(List.of(
                measurement("S", "46.0", "44.0"),
                measurement("M", "48.0", "46.0")));
        when(reviewRepository.findByProductIdAndStatus(1L, com.petitcamel.shop.review.domain.ReviewStatus.VISIBLE))
                .thenReturn(List.of());

        SizeRecommendationResponse response = service.recommend(
                new SizeRecommendationRequest(1L, null, null, null));

        assertThat(response.confidence()).isEqualTo(Confidence.UNCERTAIN);
        assertThat(response.recommendedSize()).isNull();
    }

    @Test
    void throwsWhenProductMissing() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.recommend(new SizeRecommendationRequest(99L, 160, 50, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private static ProductMeasurement measurement(String size, String chest, String waist) {
        ProductMeasurement m = new ProductMeasurement();
        m.setSize(size);
        m.setChest(new BigDecimal(chest));
        m.setWaist(new BigDecimal(waist));
        return m;
    }
}
