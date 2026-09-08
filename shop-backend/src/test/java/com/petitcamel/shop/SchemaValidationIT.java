package com.petitcamel.shop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SchemaValidationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("shop_ai")
            .withUsername("shop_ai")
            .withPassword("shop_ai123!");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void flywayMigrationsAndJpaValidateAgainstSchema() {
        List<String> tables = jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """,
                String.class);

        assertThat(tables).contains(
                "member",
                "refresh_token",
                "category",
                "product",
                "product_sku",
                "inventory",
                "orders",
                "order_item",
                "payment",
                "review",
                "ai_recommendation");

        Integer productCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Integer.class);
        assertThat(productCount).isGreaterThanOrEqualTo(4);

        Integer inventoryCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory", Integer.class);
        assertThat(inventoryCount).isGreaterThanOrEqualTo(9);

        Map<String, Object> soldOutSku = jdbcTemplate.queryForMap(
                """
                SELECT i.stock_quantity, i.reserved_quantity
                FROM inventory i
                JOIN product_sku s ON s.sku_id = i.sku_id
                WHERE s.sku_code = 'PC-PANTS-BG-L'
                """);
        assertThat(((Number) soldOutSku.get("stock_quantity")).intValue()).isZero();
    }
}
