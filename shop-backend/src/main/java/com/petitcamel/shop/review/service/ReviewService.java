package com.petitcamel.shop.review.service;

import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.order.domain.OrderEntity;
import com.petitcamel.shop.order.domain.OrderItem;
import com.petitcamel.shop.order.domain.OrderStatus;
import com.petitcamel.shop.order.repository.OrderEntityRepository;
import com.petitcamel.shop.order.repository.OrderItemRepository;
import com.petitcamel.shop.product.repository.ProductRepository;
import com.petitcamel.shop.review.domain.Review;
import com.petitcamel.shop.review.domain.ReviewStatus;
import com.petitcamel.shop.review.dto.CreateReviewRequest;
import com.petitcamel.shop.review.dto.ReviewResponse;
import com.petitcamel.shop.review.dto.UpdateReviewRequest;
import com.petitcamel.shop.review.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderEntityRepository orderEntityRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public ReviewService(
            ReviewRepository reviewRepository,
            ProductRepository productRepository,
            OrderItemRepository orderItemRepository,
            OrderEntityRepository orderEntityRepository,
            AuditLogService auditLogService,
            Clock clock) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderEntityRepository = orderEntityRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listVisible(Long productId, int page, int size) {
        requireProduct(productId);
        int pageSize = Math.min(Math.max(size, 1), 100);
        int pageNumber = Math.max(page, 0);
        Page<Review> reviews = reviewRepository.findByProductIdAndStatusOrderByCreatedAtDesc(
                productId, ReviewStatus.VISIBLE, PageRequest.of(pageNumber, pageSize));
        return PageResponse.of(
                reviews.getContent().stream().map(this::toResponse).toList(),
                reviews.getNumber(),
                reviews.getSize(),
                reviews.getTotalElements());
    }

    @Transactional
    public ReviewResponse create(Long memberId, Long productId, CreateReviewRequest request) {
        requireProduct(productId);
        OrderItem orderItem = orderItemRepository.findById(request.orderItemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "주문 상품을 찾을 수 없습니다."));
        if (!orderItem.getProductId().equals(productId)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "해당 상품의 주문 항목이 아닙니다.");
        }

        OrderEntity order = orderEntityRepository.findById(orderItem.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인 주문에만 리뷰를 작성할 수 있습니다.");
        }
        if (order.getOrderStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "결제 완료된 주문만 리뷰를 작성할 수 있습니다.");
        }

        if (reviewRepository.findByOrderItemId(orderItem.getOrderItemId()).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 해당 주문 상품에 대한 리뷰가 있습니다.");
        }

        Instant now = clock.instant();
        Review review = new Review();
        review.setMemberId(memberId);
        review.setProductId(productId);
        review.setOrderItemId(orderItem.getOrderItemId());
        review.setRating(request.rating());
        review.setContent(request.content().trim());
        review.setHeightCm(request.heightCm());
        review.setWeightKg(request.weightKg());
        review.setPurchasedSize(blankToNull(request.purchasedSize()));
        review.setFitRating(request.fitRating());
        review.setStatus(ReviewStatus.VISIBLE);
        review.setCreatedAt(now);
        review.setUpdatedAt(now);
        return toResponse(reviewRepository.save(review));
    }

    @Transactional
    public ReviewResponse update(Long memberId, Long reviewId, UpdateReviewRequest request) {
        Review review = requireOwner(memberId, reviewId);
        review.setRating(request.rating());
        review.setContent(request.content().trim());
        review.setHeightCm(request.heightCm());
        review.setWeightKg(request.weightKg());
        review.setPurchasedSize(blankToNull(request.purchasedSize()));
        review.setFitRating(request.fitRating());
        review.setUpdatedAt(clock.instant());
        return toResponse(reviewRepository.save(review));
    }

    @Transactional
    public void delete(Long memberId, Long reviewId) {
        Review review = requireOwner(memberId, reviewId);
        review.setStatus(ReviewStatus.HIDDEN);
        review.setUpdatedAt(clock.instant());
        reviewRepository.save(review);
    }

    @Transactional
    public ReviewResponse updateAdminStatus(Long reviewId, ReviewStatus newStatus, Long actorMemberId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "리뷰를 찾을 수 없습니다."));
        ReviewStatus previous = review.getStatus();
        if (previous == newStatus) {
            return toResponse(review);
        }

        review.setStatus(newStatus);
        review.setUpdatedAt(clock.instant());
        Review saved = reviewRepository.save(review);

        auditLogService.record(
                actorMemberId,
                "REVIEW_STATUS_UPDATE",
                "REVIEW",
                String.valueOf(reviewId),
                "from=" + previous + ", to=" + newStatus);

        return toResponse(saved);
    }

    private Review requireOwner(Long memberId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "리뷰를 찾을 수 없습니다."));
        if (!review.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인 리뷰만 수정·삭제할 수 있습니다.");
        }
        return review;
    }

    private void requireProduct(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }
    }

    private ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getReviewId(),
                review.getProductId(),
                review.getOrderItemId(),
                review.getRating(),
                review.getContent(),
                review.getHeightCm(),
                review.getWeightKg(),
                review.getPurchasedSize(),
                review.getFitRating(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
