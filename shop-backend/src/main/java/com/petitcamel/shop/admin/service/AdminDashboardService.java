package com.petitcamel.shop.admin.service;

import com.petitcamel.shop.admin.dto.AdminDashboardResponse;
import com.petitcamel.shop.inventory.repository.InventoryRepository;
import com.petitcamel.shop.member.repository.MemberRepository;
import com.petitcamel.shop.order.domain.OrderStatus;
import com.petitcamel.shop.order.repository.OrderEntityRepository;
import com.petitcamel.shop.product.domain.ProductStatus;
import com.petitcamel.shop.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AdminDashboardService {

    private static final int RECENT_ORDER_DAYS = 7;

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final OrderEntityRepository orderEntityRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public AdminDashboardService(
            InventoryRepository inventoryRepository,
            ProductRepository productRepository,
            OrderEntityRepository orderEntityRepository,
            MemberRepository memberRepository,
            Clock clock) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.orderEntityRepository = orderEntityRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        Instant recentSince = clock.instant().minus(RECENT_ORDER_DAYS, ChronoUnit.DAYS);
        return new AdminDashboardResponse(
                inventoryRepository.countLowStock(),
                productRepository.countByStatus(ProductStatus.ON_SALE),
                orderEntityRepository.countByOrderedAtAfter(recentSince),
                orderEntityRepository.countByOrderStatus(OrderStatus.PAYMENT_PENDING),
                memberRepository.count());
    }
}
