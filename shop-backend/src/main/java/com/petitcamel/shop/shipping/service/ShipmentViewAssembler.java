package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.shipping.domain.DeliveryCompany;
import com.petitcamel.shop.shipping.domain.ReturnRequest;
import com.petitcamel.shop.shipping.domain.Shipment;
import com.petitcamel.shop.shipping.domain.ShipmentTrackingEvent;
import com.petitcamel.shop.shipping.dto.ReturnRequestResponse;
import com.petitcamel.shop.shipping.dto.ShipmentView;
import com.petitcamel.shop.shipping.dto.TrackingEventResponse;
import com.petitcamel.shop.shipping.repository.ShipmentTrackingEventRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class ShipmentViewAssembler {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    private final ShipmentTrackingEventRepository eventRepository;
    private final DeliveryCompanyService deliveryCompanyService;

    public ShipmentViewAssembler(
            ShipmentTrackingEventRepository eventRepository,
            DeliveryCompanyService deliveryCompanyService) {
        this.eventRepository = eventRepository;
        this.deliveryCompanyService = deliveryCompanyService;
    }

    public Map<String, DeliveryCompany> companies() {
        return deliveryCompanyService.companiesByCode();
    }

    public ShipmentView toView(Shipment shipment, boolean includeEvents) {
        return toView(shipment, includeEvents, companies());
    }

    public ShipmentView toView(Shipment shipment, boolean includeEvents, Map<String, DeliveryCompany> companies) {
        if (shipment == null) {
            return null;
        }
        return new ShipmentView(
                shipment.getShipmentId(),
                shipment.getOrderId(),
                shipment.getShipmentType(),
                shipment.getShipmentStatus(),
                shipment.getShipmentStatus().getLabel(),
                shipment.getDeliveryCompany(),
                DeliveryCompanyService.companyName(companies, shipment.getDeliveryCompany()),
                shipment.getTrackingNumber(),
                DeliveryCompanyService.trackingUrl(companies, shipment.getDeliveryCompany(), shipment.getTrackingNumber()),
                shipment.getPickupRequestedAt(),
                shipment.getPickedUpAt(),
                shipment.getShippedAt(),
                shipment.getDeliveredAt(),
                shipment.getLastTrackingCheckedAt(),
                shipment.getLastTrackingError(),
                includeEvents ? events(shipment.getShipmentId()) : List.of());
    }

    public List<TrackingEventResponse> events(Long shipmentId) {
        if (shipmentId == null) {
            return List.of();
        }
        return eventRepository.findByShipmentIdOrderByEventTimeAscEventIdAsc(shipmentId).stream()
                .map(ShipmentViewAssembler::toEvent)
                .toList();
    }

    public ReturnRequestResponse toReturnResponse(ReturnRequest r, Map<String, DeliveryCompany> companies) {
        if (r == null) {
            return null;
        }
        return new ReturnRequestResponse(
                r.getReturnRequestId(),
                r.getOrderId(),
                r.getReason(),
                r.getReason().getLabel(),
                r.getMemo(),
                r.getReturnStatus(),
                r.getReturnStatus().getLabel(),
                r.getPickupName(),
                r.getPickupPhone(),
                r.getPickupPostcode(),
                r.getPickupAddress1(),
                r.getPickupAddress2(),
                r.getPickupDeliveryCompany(),
                DeliveryCompanyService.companyName(companies, r.getPickupDeliveryCompany()),
                r.getPickupTrackingNumber(),
                DeliveryCompanyService.trackingUrl(companies, r.getPickupDeliveryCompany(), r.getPickupTrackingNumber()),
                r.isFreeReturn(),
                r.getReturnShippingFee(),
                r.getRefundAmount(),
                r.isRestocked(),
                r.getRejectReason(),
                r.getRequestedAt(),
                r.getApprovedAt(),
                r.getPickupRequestedAt(),
                r.getPickedUpAt(),
                r.getReceivedAt(),
                r.getCompletedAt(),
                r.getRejectedAt());
    }

    public static String formatTime(Instant instant) {
        return instant == null ? null : DISPLAY_TIME.format(instant);
    }

    static TrackingEventResponse toEvent(ShipmentTrackingEvent event) {
        return new TrackingEventResponse(
                formatTime(event.getEventTime()),
                event.getEventTime(),
                event.getLocation(),
                event.getDescription(),
                event.getStatus() == null ? null : event.getStatus().name(),
                event.getSource().name());
    }
}
