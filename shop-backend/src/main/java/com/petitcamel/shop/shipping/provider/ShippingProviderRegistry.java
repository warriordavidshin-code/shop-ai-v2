package com.petitcamel.shop.shipping.provider;

import com.petitcamel.shop.shipping.config.ShippingProperties;
import com.petitcamel.shop.shipping.domain.ProviderCapability;
import com.petitcamel.shop.shipping.domain.ShippingProvider;
import com.petitcamel.shop.shipping.repository.ShippingProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Picks the vendor for each capability. A vendor qualifies when its {@code shipping_provider} row allows the
 * capability, a Java client implements it, and the client is configured. Among qualifying vendors the one named in
 * {@code shipping.provider} (SHIPPING_PROVIDER) wins, then external vendors by sort order, then MANUAL.
 * Rows are read on each call so admin switches apply immediately; the table has a handful of rows.
 */
@Component
public class ShippingProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(ShippingProviderRegistry.class);

    /** A qualifying vendor: its settings row and its client. */
    public record ActiveProvider(ShippingProvider settings, ShippingProviderClient client) {

        public String code() {
            return settings.getCode();
        }

        public Long id() {
            return settings.getShippingProviderId();
        }

        public boolean isManual() {
            return settings.isManual();
        }
    }

    private final Map<String, ShippingProviderClient> clients;
    private final ShippingProviderRepository repository;
    private final String preferred;

    public ShippingProviderRegistry(
            List<ShippingProviderClient> clients,
            ShippingProviderRepository repository,
            ShippingProperties properties) {
        this.clients = clients.stream()
                .collect(Collectors.toMap(ShippingProviderClient::code, Function.identity(), (a, b) -> a));
        this.repository = repository;
        this.preferred = properties.getProvider() == null || properties.getProvider().isBlank()
                ? ShippingProvider.MANUAL
                : properties.getProvider().trim().toUpperCase(Locale.ROOT);
        ShippingProviderClient preferredClient = this.clients.get(preferred);
        if (preferredClient == null) {
            log.warn("[SHIPPING] unknown provider={} (no client) -> other providers / MANUAL", preferred);
        } else if (!preferredClient.isConfigured()) {
            log.warn("[SHIPPING] provider={} is not configured (API key/endpoint missing)", preferred);
        } else {
            log.info("[SHIPPING] preferred provider={}", preferred);
        }
    }

    public Optional<ActiveProvider> resolve(ProviderCapability capability) {
        return repository.findAllByOrderBySortOrderAscShippingProviderIdAsc().stream()
                .filter(row -> qualifies(row, capability))
                .min(Comparator.comparingInt((ShippingProvider row) -> rank(row)))
                .map(row -> new ActiveProvider(row, clients.get(row.getCode())));
    }

    /** True when courier events come from an external tracking API rather than admin input only. */
    public boolean externalTrackingEnabled() {
        return resolve(ProviderCapability.TRACKING).isPresent();
    }

    public String preferredProvider() {
        return preferred;
    }

    public Optional<ShippingProviderClient> client(String code) {
        return Optional.ofNullable(clients.get(code));
    }

    public boolean qualifies(ShippingProvider row, ProviderCapability capability) {
        ShippingProviderClient client = clients.get(row.getCode());
        return row.allows(capability) && client != null && client.supports(capability) && client.isConfigured();
    }

    private int rank(ShippingProvider row) {
        if (row.getCode().equals(preferred)) {
            return 0;
        }
        return row.isManual() ? 2 : 1;
    }
}
