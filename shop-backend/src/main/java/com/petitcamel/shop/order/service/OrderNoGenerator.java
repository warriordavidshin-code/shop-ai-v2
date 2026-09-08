package com.petitcamel.shop.order.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class OrderNoGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private OrderNoGenerator() {
    }

    public static String generate(Clock clock) {
        String timestamp = LocalDateTime.now(clock).format(FORMATTER);
        int suffix = RANDOM.nextInt(10_000);
        return "PC" + timestamp + String.format("%04d", suffix);
    }
}
