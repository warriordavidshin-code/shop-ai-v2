package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.shipping.domain.DeliveryCompany;
import com.petitcamel.shop.shipping.domain.ReturnRequest;
import com.petitcamel.shop.shipping.domain.Shipment;
import com.petitcamel.shop.shipping.domain.ShipmentTrackingEvent;
import com.petitcamel.shop.shipping.domain.ShippingProvider;
import com.petitcamel.shop.shipping.dto.ReturnRequestResponse;
import com.petitcamel.shop.shipping.dto.ShipmentView;
import com.petitcamel.shop.shipping.dto.TrackingEventResponse;
import com.petitcamel.shop.shipping.repository.ShipmentTrackingEventRepository;
import com.petitcamel.shop.shipping.repository.ShippingProviderRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds shipment / return DTOs. Couriers and vendors are loaded once per request ({@link Refs}). */
@Component
public class ShipmentViewAssembler {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    /** Small lookup tables shared by all rows of one response. */
    public record Refs(Map<Long, DeliveryCompany> companies, Map<Long, ShippingProvider> providers) {

        public String companyCode(Long id) {
            return DeliveryCompanyService.companyCode(companies, id);
        }

        public String companyName(Long id) {
            return DeliveryCompanyService.companyName(companies, id);
        }

        public String trackingUrl(Long companyId, String trackingNumber) {
            return DeliveryCompanyService.trackingUrl(companies, companyId, trackingNumber);
        }

        public String providerCode(Long id) {
            ShippingProvider provider = id == null ? null : providers.get(id);
            return provider == null ? null : provider.getCode();
        }
    }

    private final ShipmentTrackingEventRepository eventRepository;
    private final DeliveryCompanyService deliveryCompanyService;
    private final ShippingProviderRepository providerRepository;

    public ShipmentViewAssembler(
            ShipmentTrackingEventRepository eventRepository,
            DeliveryCompanyService deliveryCompanyService,
            ShippingProviderRepository providerRepository) {
        this.eventRepository = eventRepository;
        this.deliveryCompanyService = deliveryCompanyService;
        this.providerRepository = providerRepository;
    }

    public Refs refs() {
        return new Refs(
                deliveryCompanyService.companiesById(),
                providerRepository.findAll().stream()
                        .collect(Collectors.toMap(ShippingProvider::getShippingProviderId, Function.identity())));
    }

    public ShipmentView toView(Shipment shipment, boolean includeEvents) {
        return shipment == null ? null : toView(shipment, includeEvents, refs());
    }

    public ShipmentView toView(Shipment shipment, boolean includeEvents, Refs refs) {
        if (shipment == null) {
            return null;
        }
        return new ShipmentView(
                shipment.getShipmentId(),
                shipment.getOrderId(),
                shipment.getShipmentType(),
                shipment.getStatus(),
                shipment.getStatus().labelFor(shipment.getShipmentType()),
                refs.companyCode(shipment.getDeliveryCompanyId()),
                refs.companyName(shipment.getDeliveryCompanyId()),
                shipment.getTrackingNumber(),
                refs.trackingUrl(shipment.getDeliveryCompanyId(), shipment.getTrackingNumber()),
                refs.providerCode(shipment.getShippingProviderId()),
                shipment.getPickupRequestedAt(),
                shipment.getPickedUpAt(),
                shipment.getShippedAt(),
                shipment.getOutForDeliveryAt(),
                shipment.getDeliveredAt(),
                shipment.getLastTrackingCheckedAt(),
                shipment.getLastTrackingError(),
                shipment.getLastStatusChangedAt(),
                includeEvents ? events(shipment.getShipmentId()) : List.of());
    }

    public List<TrackingEventResponse> events(Long shipmentId) {
        if (shipmentId == null) {
            return List.of();
        }
        return eventRepository.findByShipmentIdOrderByEventAtAscTrackingEventIdAsc(shipmentId).stream()
                .map(ShipmentViewAssembler::toEvent)
                .toList();
    }

    /** @param returnShipment the request's RETURN shipment (pickup address, courier, invoice), may be null */
    public ReturnRequestResponse toReturnResponse(ReturnRequest r, Shipment returnShipment, Refs refs) {
        if (r == null) {
            return null;
        }
        Shipment s = returnShipment;
        return new ReturnRequestResponse(
                r.getReturnRequestId(),
                r.getOrderId(),
                r.getReasonCode(),
                r.getReasonCode().getLabel(),
                r.getReasonText(),
                r.getCustomerMemo(),
                r.getStatus(),
                r.getStatus().getLabel(),
                s == null ? null : s.getContactName(),
                s == null ? null : s.getContactPhone(),
                s == null ? null : s.getPostalCode(),
                s == null ? null : s.getAddress1(),
                s == null ? null : s.getAddress2(),
                s == null ? null : refs.companyCode(s.getDeliveryCompanyId()),
                s == null ? null : refs.companyName(s.getDeliveryCompanyId()),
                s == null ? null : s.getTrackingNumber(),
                s == null ? null : refs.trackingUrl(s.getDeliveryCompanyId(), s.getTrackingNumber()),
                s == null ? null : s.getStatus().name(),
                s == null ? null : s.getStatus().labelFor(s.getShipmentType()),
                r.isFreeReturn(),
                r.getReturnShippingFee(),
                r.getRefundAmount(),
                r.isRestocked(),
                r.getRejectReason(),
                r.getRequestedAt(),
                r.getApprovedAt(),
                s == null ? null : s.getPickupRequestedAt(),
                s == null ? null : s.getPickedUpAt(),
                r.getReceivedAt(),
                r.getCompletedAt(),
                r.getRejectedAt(),
                r.getCancelledAt());
    }

    public static String formatTime(Instant instant) {
        return instant == null ? null : DISPLAY_TIME.format(instant);
    }

    static TrackingEventResponse toEvent(ShipmentTrackingEvent event) {
        return new TrackingEventResponse(
                formatTime(event.getEventAt()),
                event.getEventAt(),
                event.getLocation(),
                event.getDescription(),
                event.getStatus() == null ? null : event.getStatus().name(),
                event.getProviderStatus(),
                event.getSource().name());
    }
}
