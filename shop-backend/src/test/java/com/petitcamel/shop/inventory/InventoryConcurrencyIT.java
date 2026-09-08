package com.petitcamel.shop.inventory;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.inventory.domain.Inventory;
import com.petitcamel.shop.inventory.dto.InventoryAdjustRequest;
import com.petitcamel.shop.inventory.repository.InventoryRepository;
import com.petitcamel.shop.inventory.service.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_IT", matches = "true")
class InventoryConcurrencyIT {

    private static final long SKU_ID = 3L;

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/shop_ai"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("DB_USERNAME", "shop_ai"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("DB_PASSWORD", "shop_ai123!"));
    }

    @Autowired
    InventoryService inventoryService;

    @Autowired
    InventoryRepository inventoryRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetInventory() {
        jdbcTemplate.update(
                "UPDATE inventory SET stock_quantity = 1, reserved_quantity = 0, version = 0, updated_at = NOW() WHERE sku_id = ?",
                SKU_ID);
        jdbcTemplate.update("DELETE FROM inventory_movement WHERE sku_id = ?", SKU_ID);
    }

    @AfterEach
    void restoreSampleStock() {
        jdbcTemplate.update(
                "UPDATE inventory SET stock_quantity = 10, reserved_quantity = 0, version = 0, updated_at = NOW() WHERE sku_id = ?",
                SKU_ID);
    }

    @Test
    void adjustThatWouldGoNegativeFails() {
        assertThatThrownBy(() -> inventoryService.adjust(
                        SKU_ID,
                        new InventoryAdjustRequest(-2, "over-reduce"),
                        null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));

        Inventory inventory = inventoryRepository.findBySkuId(SKU_ID).orElseThrow();
        assertThat(inventory.getStockQuantity()).isEqualTo(1);
        assertThat(inventory.getStockQuantity()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void concurrentAdjustOnlyOneSucceedsOrStockNeverNegative() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<Throwable> errors = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        inventoryService.adjust(
                                SKU_ID,
                                new InventoryAdjustRequest(-1, "concurrent"),
                                null);
                        successes.incrementAndGet();
                    } catch (BusinessException ex) {
                        failures.incrementAndGet();
                        if (ex.getCode() != ErrorCode.CONFLICT
                                && ex.getCode() != ErrorCode.BUSINESS_RULE_VIOLATION) {
                            synchronized (errors) {
                                errors.add(ex);
                            }
                        }
                    } catch (Exception ex) {
                        synchronized (errors) {
                            errors.add(ex);
                        }
                    }
                    return null;
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(errors).isEmpty();
        assertThat(successes.get() + failures.get()).isEqualTo(2);
        assertThat(successes.get()).isBetween(1, 2);
        assertThat(failures.get()).isBetween(0, 1);

        Inventory inventory = inventoryRepository.findBySkuId(SKU_ID).orElseThrow();
        assertThat(inventory.getStockQuantity()).isGreaterThanOrEqualTo(0);
        assertThat(inventory.getStockQuantity()).isLessThanOrEqualTo(1);
        if (successes.get() == 2) {
            assertThat(inventory.getStockQuantity()).isZero();
        } else {
            assertThat(successes.get()).isEqualTo(1);
            assertThat(failures.get()).isEqualTo(1);
            assertThat(inventory.getStockQuantity()).isZero();
        }
    }
}
