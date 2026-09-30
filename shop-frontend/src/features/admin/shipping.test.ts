import { describe, expect, it } from "vitest";
import {
  adminOrderRowSchema,
  canAdvanceShipment,
  canAllowRetry,
  extraAreaSchema,
  isPickupRequested,
  shipmentToTracking,
  toOrderView,
  toReturnFilter,
} from "./shipping";

describe("admin shipping helpers", () => {
  it("accepts only known list views and return filters", () => {
    expect(toOrderView("SHIPPING")).toBe("SHIPPING");
    expect(toOrderView("bogus")).toBe("ALL");
    expect(toOrderView(undefined)).toBe("ALL");
    expect(toReturnFilter("COMPLETED")).toBe("COMPLETED");
    expect(toReturnFilter("REFUNDED")).toBe("OPEN");
    expect(toReturnFilter(null)).toBe("OPEN");
  });

  it("offers retry only for calls whose vendor outcome is unresolved", () => {
    expect(canAllowRetry({ status: "UNKNOWN", needsAttention: true })).toBe(true);
    expect(canAllowRetry({ status: "PENDING", needsAttention: true })).toBe(true);
    expect(canAllowRetry({ status: "PENDING", needsAttention: false })).toBe(false);
    expect(canAllowRetry({ status: "SUCCEEDED", needsAttention: true })).toBe(false);
    expect(canAllowRetry({ status: "FAILED", needsAttention: false })).toBe(false);
  });

  it("allows manual shipment changes only forward, like the server", () => {
    expect(canAdvanceShipment("WAYBILL_ISSUED", "PICKED_UP")).toBe(true);
    expect(canAdvanceShipment("IN_TRANSIT", "PICKED_UP")).toBe(false);
    expect(canAdvanceShipment("IN_TRANSIT", "FAILED")).toBe(true);
    expect(canAdvanceShipment("DELIVERED", "FAILED")).toBe(false);
    expect(canAdvanceShipment("PICKED_UP", "CANCELLED")).toBe(false);
    expect(canAdvanceShipment(null, "DELIVERED")).toBe(true);
    expect(isPickupRequested("WAYBILL_ISSUED")).toBe(false);
    expect(isPickupRequested("PICKUP_REQUESTED")).toBe(true);
    expect(isPickupRequested("CANCELLED")).toBe(true);
  });

  it("parses an extra area without an area-specific fee", () => {
    const area = extraAreaSchema.parse({
      areaId: 3,
      areaType: "JEJU",
      areaName: "제주",
      postalCodeFrom: "63000",
      postalCodeTo: "63644",
      extraFee: null,
      effectiveFee: 3000,
      enabled: true,
      source: "SEED",
    });
    expect(area.extraFee).toBeNull();
    expect(area.effectiveFee).toBe(3000);
  });

  it("parses an order row with numeric strings and missing delivery data", () => {
    const row = adminOrderRowSchema.parse({
      orderId: 1,
      orderNo: "ORD-1",
      orderStatus: "PAID",
      paymentAmount: "53000.00",
      shipmentStatus: null,
      trackingNumber: null,
    });
    expect(row.paymentAmount).toBe(53000);
    expect(row.trackingNumber).toBeNull();
  });

  it("adapts a shipment for the tracking modal", () => {
    const tracking = shipmentToTracking(
      {
        shipmentId: 5,
        orderId: 1,
        shipmentType: "DELIVERY",
        status: "IN_TRANSIT",
        statusName: "배송중",
        deliveryCompany: "CJ",
        deliveryCompanyName: "CJ대한통운",
        trackingNumber: "123456789012",
        lastTrackingError: "HTTP 500",
        events: [{ time: "2026-09-29 13:10", description: "집하", source: "PROVIDER" }],
      },
      { orderId: 1, orderNo: "ORD-1", orderStatus: "SHIPPED" },
    );
    expect(tracking?.statusName).toBe("배송중");
    expect(tracking?.events).toHaveLength(1);
    expect(tracking?.message).toContain("일시적으로");
    expect(shipmentToTracking(null, { orderId: 1, orderNo: "ORD-1", orderStatus: "PAID" })).toBeNull();
  });
});
