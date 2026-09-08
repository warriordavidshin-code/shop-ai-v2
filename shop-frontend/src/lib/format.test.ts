import { describe, expect, it } from "vitest";
import { discountRate, formatKrw } from "./format";

describe("formatKrw", () => {
  it("formats with ko-KR grouping", () => {
    expect(formatKrw(49000)).toBe("49,000");
  });
});

describe("discountRate", () => {
  it("calculates rounded percent", () => {
    expect(discountRate(59000, 49000)).toBe(17);
  });

  it("returns 0 when no discount", () => {
    expect(discountRate(10000, 10000)).toBe(0);
  });
});
