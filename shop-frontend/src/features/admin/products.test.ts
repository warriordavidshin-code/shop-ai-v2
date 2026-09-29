import { describe, expect, it } from "vitest";
import { adminProductPageSchema, buildSkuCode, parseStockInput, stockSummary } from "./products";

describe("parseStockInput", () => {
  it("accepts whole non-negative numbers", () => {
    expect(parseStockInput("0")).toBe(0);
    expect(parseStockInput(" 25 ")).toBe(25);
  });

  it("rejects negatives, decimals, blanks and huge values", () => {
    expect(parseStockInput("-1")).toBeNull();
    expect(parseStockInput("1.5")).toBeNull();
    expect(parseStockInput("")).toBeNull();
    expect(parseStockInput("abc")).toBeNull();
    expect(parseStockInput("1000001")).toBeNull();
  });
});

describe("stockSummary", () => {
  it("sums stock, reserved and available across SKUs", () => {
    expect(
      stockSummary([
        { stockQuantity: 10, reservedQuantity: 2, availableQuantity: 8 },
        { stockQuantity: 5, reservedQuantity: 0, availableQuantity: 5 },
      ]),
    ).toEqual({ stock: 15, reserved: 2, available: 13 });
    expect(stockSummary([])).toEqual({ stock: 0, reserved: 0, available: 0 });
  });
});

describe("buildSkuCode", () => {
  it("normalizes color and size into a code", () => {
    expect(buildSkuCode(12, "Ivory", "free")).toBe("P12-IVORY-FREE");
    expect(buildSkuCode(3, " 라이트 블루 ", "M/L")).toBe("P3-라이트-블루-M-L");
    expect(buildSkuCode(3, "!!", "")).toBe("P3-X-X");
  });
});

describe("adminProductPageSchema", () => {
  it("parses the admin product list response with SKU stock", () => {
    const parsed = adminProductPageSchema.parse({
      content: [
        {
          productId: 1,
          productName: "Knit",
          brandName: null,
          status: "ON_SALE",
          normalPrice: "59000.00",
          salePrice: 49000,
          discountRate: 17,
          skus: [
            {
              skuId: 3,
              skuCode: "K-IV-F",
              color: "Ivory",
              size: "F",
              status: "ON_SALE",
              stockQuantity: 10,
              reservedQuantity: 1,
              availableQuantity: 9,
            },
          ],
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    expect(parsed.content[0].normalPrice).toBe(59000);
    expect(parsed.content[0].skus[0].availableQuantity).toBe(9);
  });
});
