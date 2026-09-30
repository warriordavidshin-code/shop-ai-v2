package com.petitcamel.shop.shipping.provider;

import com.petitcamel.shop.shipping.config.ShippingProperties;
import com.petitcamel.shop.shipping.provider.manual.ManualShippingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Selects the active provider from {@code shipping.provider}; unconfigured providers fall back to MANUAL. */
@Component
public class ShippingProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(ShippingProviderRegistry.class);

    private final ShippingProvider active;
    private final String requested;

    public ShippingProviderRegistry(List<ShippingProvider> providers, ShippingProperties properties) {
        this.requested = properties.getProvider() == null || properties.getProvider().isBlank()
                ? ManualShippingProvider.NAME
                : properties.getProvider().trim().toUpperCase(Locale.ROOT);
        ShippingProvider manual = providers.stream()
                .filter(p -> ManualShippingProvider.NAME.equals(p.name()))
                .findFirst()
                .orElseGet(ManualShippingProvider::new);
        ShippingProvider selected = providers.stream()
                .filter(p -> p.name().equals(requested))
                .findFirst()
                .orElse(null);

        if (selected == null) {
            log.warn("[SHIPPING] unknown provider={} -> MANUAL", requested);
            this.active = manual;
        } else if (!selected.isConfigured()) {
            log.warn("[SHIPPING] provider={} is not configured (API key/endpoint missing) -> MANUAL", requested);
            this.active = manual;
        } else {
            log.info("[SHIPPING] provider={} active", selected.name());
            this.active = selected;
        }
    }

    public ShippingProvider active() {
        return active;
    }

    public String requestedProvider() {
        return requested;
    }

    public boolean externalTrackingEnabled() {
        return !ManualShippingProvider.NAME.equals(active.name());
    }
}
