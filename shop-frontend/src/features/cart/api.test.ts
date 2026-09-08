import { describe, expect, it } from "vitest";
import { cartTotals } from "./api";

describe("cartTotals", () => {
  it("adds delivery when there are items", () => {
    expect(cartTotals([{ unitPrice: 10000, quantity: 2 }])).toEqual({
      productAmount: 20000,
      deliveryAmount: 3000,
      paymentAmount: 23000,
    });
  });

  it("zero delivery for empty cart", () => {
    expect(cartTotals([])).toEqual({
      productAmount: 0,
      deliveryAmount: 0,
      paymentAmount: 0,
    });
  });
});
