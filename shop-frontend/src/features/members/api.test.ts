import { describe, expect, it } from "vitest";
import { addressFormSchema, formatAddressLine, socialReauthUrl } from "./api";

describe("member address helpers", () => {
  it("formats an address line with optional detail", () => {
    expect(formatAddressLine({ postcode: "06236", address1: "서울 강남구 테헤란로 1", address2: "3층" })).toBe(
      "(06236) 서울 강남구 테헤란로 1 3층",
    );
    expect(formatAddressLine({ postcode: "06236", address1: "서울 강남구 테헤란로 1", address2: null })).toBe(
      "(06236) 서울 강남구 테헤란로 1",
    );
  });

  it("validates address form input", () => {
    const valid = addressFormSchema.safeParse({
      label: "집",
      receiverName: "홍길동",
      receiverPhone: "010-1234-5678",
      postcode: "06236",
      address1: "서울 강남구 테헤란로 1",
      address2: "",
      defaultAddress: true,
    });
    expect(valid.success).toBe(true);

    const invalid = addressFormSchema.safeParse({
      label: "",
      receiverName: "홍길동",
      receiverPhone: "abc",
      postcode: "",
      address1: "",
      defaultAddress: false,
    });
    expect(invalid.success).toBe(false);
  });

  it("builds the social re-authentication URL through the BFF proxy", () => {
    expect(socialReauthUrl("KAKAO")).toBe("/api/shop/auth/kakao/reauth?redirect=%2Fmypage%2Fprofile");
    expect(socialReauthUrl("NAVER", "/mypage")).toBe("/api/shop/auth/naver/reauth?redirect=%2Fmypage");
  });
});
