package com.petitcamel.shop.shipping.provider;

import com.petitcamel.shop.shipping.domain.ShipmentStatus;

/**
 * Maps courier status text / levels to internal statuses. Couriers word their events differently
 * ("집화처리", "상품인수", "간선상차", "배달출발" ...), so matching is keyword based. The mapping is the same for
 * deliveries and returns; the shipment type tells the direction.
 */
public final class TrackingStatusMapper {

    private TrackingStatusMapper() {
    }

    /** SweetTracker level: 1 배송준비중, 2 집화완료, 3 배송중, 4 지점도착, 5 배송출발, 6 배송완료. */
    public static ShipmentStatus fromSweetTrackerLevel(Integer level) {
        if (level == null) {
            return null;
        }
        return switch (level) {
            case 2 -> ShipmentStatus.PICKED_UP;
            case 3, 4 -> ShipmentStatus.IN_TRANSIT;
            case 5 -> ShipmentStatus.OUT_FOR_DELIVERY;
            case 6 -> ShipmentStatus.DELIVERED;
            default -> null;
        };
    }

    public static ShipmentStatus fromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.replace(" ", "");
        if (t.contains("배달완료") || t.contains("배송완료") || t.contains("수령") || t.contains("인도완료")) {
            return ShipmentStatus.DELIVERED;
        }
        if (t.contains("배달출발") || t.contains("배송출발") || t.contains("배달준비") || t.contains("배송기사")) {
            return ShipmentStatus.OUT_FOR_DELIVERY;
        }
        if (t.contains("집화") || t.contains("집하") || t.contains("상품인수") || t.equals("인수")) {
            return ShipmentStatus.PICKED_UP;
        }
        if (t.contains("이동중") || t.contains("간선") || t.contains("상차") || t.contains("하차")
                || t.contains("도착") || t.contains("발송") || t.contains("터미널") || t.contains("배송중")) {
            return ShipmentStatus.IN_TRANSIT;
        }
        return null;
    }
}
