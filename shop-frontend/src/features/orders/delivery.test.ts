import { describe, expect, it } from "vitest";
import { amountUntilFreeShipping, deliveryFeeFor, freeShippingMessage } from "./delivery";

describe("delivery fee policy", () => {
  it("charges 3,000원 below 50,000원 and nothing from 50,000원", () => {
    expect(deliveryFeeFor(0)).toBe(0);
    expect(deliveryFeeFor(49999)).toBe(3000);
    expect(deliveryFeeFor(50000)).toBe(0);
    expect(deliveryFeeFor(98000)).toBe(0);
  });

  it("reports the remaining amount for free shipping", () => {
    expect(amountUntilFreeShipping(30000)).toBe(20000);
    expect(amountUntilFreeShipping(60000)).toBe(0);
  });

  it("builds a customer-facing message", () => {
    expect(freeShippingMessage(60000)).toBe("50,000원 이상 결제 시 무료배송이 적용되었습니다.");
    expect(freeShippingMessage(30000)).toBe(
      "20,000원 더 구매하시면 무료배송입니다. (50,000원 이상 결제 시 무료배송)",
    );
  });
});
