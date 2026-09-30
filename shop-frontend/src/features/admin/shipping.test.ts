import { describe, expect, it } from "vitest";
import { adminOrderRowSchema, shipmentToTracking, toOrderView, toReturnFilter } from "./shipping";

describe("admin shipping helpers", () => {
  it("accepts only known list views and return filters", () => {
    expect(toOrderView("SHIPPING")).toBe("SHIPPING");
    expect(toOrderView("bogus")).toBe("ALL");
    expect(toOrderView(undefined)).toBe("ALL");
    expect(toReturnFilter("REFUNDED")).toBe("REFUNDED");
    expect(toReturnFilter(null)).toBe("OPEN");
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
