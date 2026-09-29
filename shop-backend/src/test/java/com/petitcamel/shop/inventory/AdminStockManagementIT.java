package com.petitcamel.shop.inventory;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.inventory.dto.InventoryResponse;
import com.petitcamel.shop.inventory.dto.InventoryStockRequest;
import com.petitcamel.shop.inventory.service.InventoryService;
import com.petitcamel.shop.product.dto.AdminProductRequest;
import com.petitcamel.shop.product.dto.AdminProductResponse;
import com.petitcamel.shop.product.service.AdminProductService;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_IT", matches = "true")
class AdminStockManagementIT {

    private static final long SKU_ID = 3L;
    private static final String NEW_SKU_CODE = "IT-STOCK-NEW-SKU";

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
    AdminProductService adminProductService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetInventory() {
        jdbcTemplate.update(
                "UPDATE inventory SET stock_quantity = 1, reserved_quantity = 0, version = 0, updated_at = NOW() WHERE sku_id = ?",
                SKU_ID);
        jdbcTemplate.update("DELETE FROM inventory_movement WHERE sku_id = ?", SKU_ID);
        deleteNewSku();
    }

    @AfterEach
    void restore() {
        jdbcTemplate.update(
                "UPDATE inventory SET stock_quantity = 10, reserved_quantity = 0, version = 0, updated_at = NOW() WHERE sku_id = ?",
                SKU_ID);
        jdbcTemplate.update("DELETE FROM inventory_movement WHERE sku_id = ?", SKU_ID);
        deleteNewSku();
    }

    private void deleteNewSku() {
        jdbcTemplate.update(
                "DELETE FROM inventory WHERE sku_id IN (SELECT sku_id FROM product_sku WHERE sku_code = ?)",
                NEW_SKU_CODE);
        jdbcTemplate.update("DELETE FROM product_sku WHERE sku_code = ?", NEW_SKU_CODE);
    }

    private long productIdOfSku() {
        Long productId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM product_sku WHERE sku_id = ?", Long.class, SKU_ID);
        assertThat(productId).isNotNull();
        return productId;
    }

    private int movementSum() {
        Integer sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM inventory_movement WHERE sku_id = ? AND movement_type = 'ADJUSTMENT'",
                Integer.class, SKU_ID);
        return sum == null ? 0 : sum;
    }

    @Test
    void setStockUpdatesAbsoluteQuantityAndRecordsMovement() {
        InventoryResponse response = inventoryService.setStock(SKU_ID, new InventoryStockRequest(7, null), null);

        assertThat(response.stockQuantity()).isEqualTo(7);
        assertThat(response.availableQuantity()).isEqualTo(7);
        assertThat(movementSum()).isEqualTo(6);
    }

    @Test
    void setStockWithSameValueRecordsNothing() {
        InventoryResponse response = inventoryService.setStock(SKU_ID, new InventoryStockRequest(1, null), null);

        assertThat(response.stockQuantity()).isEqualTo(1);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_movement WHERE sku_id = ?", Integer.class, SKU_ID);
        assertThat(count).isZero();
    }

    @Test
    void setStockBelowReservedFails() {
        jdbcTemplate.update("UPDATE inventory SET stock_quantity = 5, reserved_quantity = 3 WHERE sku_id = ?", SKU_ID);

        assertThatThrownBy(() -> inventoryService.setStock(SKU_ID, new InventoryStockRequest(2, null), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));
    }

    @Test
    void addSkuCreatesOptionWithInitialStock() {
        long productId = productIdOfSku();
        AdminProductResponse response = adminProductService.addSku(productId, new AdminProductRequest.SkuRequest(
                null, NEW_SKU_CODE, "IT-Color", "IT-Size", null, null, 12));

        AdminProductResponse.AdminSkuResponse created = response.skus().stream()
                .filter(sku -> NEW_SKU_CODE.equals(sku.skuCode()))
                .findFirst()
                .orElseThrow();
        assertThat(created.stockQuantity()).isEqualTo(12);
        assertThat(created.availableQuantity()).isEqualTo(12);

        assertThatThrownBy(() -> adminProductService.addSku(productId, new AdminProductRequest.SkuRequest(
                        null, NEW_SKU_CODE, "Other", "Other", null, null, 1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void updateProductRejectsSalePriceAboveNormalPrice() {
        long productId = productIdOfSku();
        AdminProductResponse product = adminProductService.getProduct(productId);
        AdminProductRequest request = new AdminProductRequest(
                product.categoryId(),
                product.productName(),
                product.brandName(),
                product.summary(),
                product.description(),
                new BigDecimal("10000"),
                new BigDecimal("12000"),
                product.status(),
                null, null, null, null, null, null,
                null,
                null);

        assertThatThrownBy(() -> adminProductService.updateProduct(productId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
