package com.petitcamel.shop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Flyway + JPA validate against local docker-compose PostgreSQL.
 * Run: docker compose up -d postgres
 * Then: mvn -Dtest=SchemaValidationLocalIT test
 * (Sets RUN_LOCAL_DB_IT=true automatically via surefire or env)
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_IT", matches = "true")
class SchemaValidationLocalIT {

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
