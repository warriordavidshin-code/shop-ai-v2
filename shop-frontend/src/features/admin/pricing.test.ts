import { describe, expect, it } from "vitest";
import { clampDiscountRate, discountRateFrom, salePriceFromRate, validatePrices } from "./pricing";

describe("salePriceFromRate", () => {
  it("applies the discount rate to the normal price", () => {
    expect(salePriceFromRate(59000, 20)).toBe(47200);
    expect(salePriceFromRate("59,000", "10")).toBe(53100);
    expect(salePriceFromRate(59000, 0)).toBe(59000);
    expect(salePriceFromRate(59000, 100)).toBe(0);
  });

  it("rounds to the nearest won", () => {
    expect(salePriceFromRate(9999, 15)).toBe(8499);
  });

  it("clamps the rate into 0..100", () => {
    expect(salePriceFromRate(10000, 150)).toBe(0);
    expect(salePriceFromRate(10000, -5)).toBe(10000);
  });

  it("returns null for invalid input", () => {
    expect(salePriceFromRate("abc", 10)).toBeNull();
    expect(salePriceFromRate(-1, 10)).toBeNull();
  });
});

describe("discountRateFrom", () => {
  it("derives the rate from normal and sale price", () => {
    expect(discountRateFrom(59000, 49000)).toBe(17);
    expect(discountRateFrom(10000, 10000)).toBe(0);
    expect(discountRateFrom(0, 0)).toBe(0);
  });

  it("returns null for invalid input", () => {
    expect(discountRateFrom(10000, "")).toBeNull();
    expect(discountRateFrom("x", 1)).toBeNull();
  });
});

describe("clampDiscountRate / validatePrices", () => {
  it("clamps rate", () => {
    expect(clampDiscountRate(NaN)).toBe(0);
    expect(clampDiscountRate(42)).toBe(42);
  });

  it("rejects sale price above normal price", () => {
    expect(validatePrices(10000, 12000)).toMatch("판매가");
    expect(validatePrices(10000, 9000)).toBeNull();
    expect(validatePrices("", 0)).toMatch("정상가");
    expect(validatePrices(-1, 0)).toMatch("정상가");
  });
});
