import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/client";
import { isPaidApprovalRequired, usd } from "@/features/admin/ops";
import { recoListSchema } from "@/features/recommendations/api";

describe("recoListSchema", () => {
  it("parses a recommendation list with reasons", () => {
    const parsed = recoListSchema.parse({
      requestId: "v1.DETAIL_SIMILAR.A.abc.def.sig",
      placement: "DETAIL_SIMILAR",
      title: "이 상품과 비슷한 상품",
      algorithm: "SIMILAR",
      items: [
        {
          product: {
            productId: 1,
            productName: "니트",
            brandName: "B",
            normalPrice: "39000",
            salePrice: 29000,
            discountRate: 25,
            mainImageUrl: null,
            colorCount: 2,
            soldOut: false,
            wishlisted: false,
          },
          reason: "같은 카테고리예요",
        },
      ],
    });
    expect(parsed.items[0].product.normalPrice).toBe(39000);
    expect(parsed.items[0].product.imageUrls).toEqual([]);
  });

  it("accepts a disabled list without request id", () => {
    const parsed = recoListSchema.parse({ requestId: null, placement: "HOME_PERSONAL", title: "t", algorithm: "DISABLED", items: [] });
    expect(parsed.items).toHaveLength(0);
  });
});

describe("AI cost helpers", () => {
  it("detects the paid approval error code", () => {
    expect(isPaidApprovalRequired(new ApiError("승인 필요", 409, "AI_PAID_APPROVAL_REQUIRED"))).toBe(true);
    expect(isPaidApprovalRequired(new ApiError("한도", 429, "AI_LIMIT_EXCEEDED"))).toBe(false);
    expect(isPaidApprovalRequired(new Error("x"))).toBe(false);
  });

  it("formats USD", () => {
    expect(usd(0.00123)).toBe("$0.0012");
    expect(usd("1.5", 2)).toBe("$1.50");
    expect(usd(null)).toBe("-");
  });
});
