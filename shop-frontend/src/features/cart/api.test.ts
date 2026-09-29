import { describe, expect, it } from "vitest";
import { cartTotals } from "./api";

describe("cartTotals", () => {
  it("adds delivery below the free-shipping threshold", () => {
    expect(cartTotals([{ unitPrice: 10000, quantity: 2 }])).toEqual({
      productAmount: 20000,
      deliveryAmount: 3000,
      paymentAmount: 23000,
    });
  });

  it("ships free from 50,000원", () => {
    expect(cartTotals([{ unitPrice: 25000, quantity: 2 }])).toEqual({
      productAmount: 50000,
      deliveryAmount: 0,
      paymentAmount: 50000,
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
