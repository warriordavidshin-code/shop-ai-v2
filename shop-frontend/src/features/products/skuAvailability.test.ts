import { describe, expect, it } from "vitest";

function isSkuSelectable(availableQuantity: number): boolean {
  return availableQuantity > 0;
}

describe("SKU availability", () => {
  it("disables sold-out sku", () => {
    expect(isSkuSelectable(0)).toBe(false);
    expect(isSkuSelectable(3)).toBe(true);
  });
});
