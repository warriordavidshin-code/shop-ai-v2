import { describe, expect, it } from "vitest";
import { DELIVERY_STEPS, deliveryStepIndex, returnStepIndex } from "./progress";

describe("delivery progress", () => {
  it("highlights the step matching the shipment status", () => {
    expect(DELIVERY_STEPS[deliveryStepIndex("SHIPPED", "PICKED_UP")].label).toBe("집하완료");
    expect(DELIVERY_STEPS[deliveryStepIndex("SHIPPED", "IN_TRANSIT")].label).toBe("배송중");
    expect(DELIVERY_STEPS[deliveryStepIndex("SHIPPED", "OUT_FOR_DELIVERY")].label).toBe("배송출발");
    expect(DELIVERY_STEPS[deliveryStepIndex("DELIVERED", "DELIVERED")].label).toBe("배송완료");
  });

  it("treats pre-pickup statuses as 상품준비", () => {
    expect(deliveryStepIndex("PREPARING", "READY")).toBe(0);
    expect(deliveryStepIndex("PREPARING", "WAYBILL_ISSUED")).toBe(0);
    expect(deliveryStepIndex("PREPARING", "PICKUP_REQUESTED")).toBe(0);
  });

  it("falls back to the order status without a shipment", () => {
    expect(deliveryStepIndex("PAID")).toBe(0);
    expect(deliveryStepIndex("SHIPPED", null)).toBe(2);
    expect(deliveryStepIndex("RETURN_REQUESTED")).toBe(4);
  });

  it("returns -1 outside the delivery flow", () => {
    expect(deliveryStepIndex("PAYMENT_PENDING")).toBe(-1);
    expect(deliveryStepIndex("CANCELLED", "CANCELLED")).toBe(-1);
  });

  it("indexes return steps", () => {
    expect(returnStepIndex("REQUESTED")).toBe(0);
    expect(returnStepIndex("IN_PROGRESS")).toBe(3);
    expect(returnStepIndex("COMPLETED")).toBe(5);
    expect(returnStepIndex("REJECTED")).toBe(-1);
    expect(returnStepIndex("CANCELLED")).toBe(-1);
    expect(returnStepIndex(null)).toBe(-1);
  });
});
