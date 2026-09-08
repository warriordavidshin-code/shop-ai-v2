package com.petitcamel.shop.review.repository;

import com.petitcamel.shop.review.domain.Review;
import com.petitcamel.shop.review.domain.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductId(Long productId);

    List<Review> findByProductIdAndStatus(Long productId, ReviewStatus status);

    Page<Review> findByProductIdAndStatusOrderByCreatedAtDesc(
            Long productId, ReviewStatus status, Pageable pageable);

    Optional<Review> findByOrderItemId(Long orderItemId);

    List<Review> findByMemberId(Long memberId);
}
