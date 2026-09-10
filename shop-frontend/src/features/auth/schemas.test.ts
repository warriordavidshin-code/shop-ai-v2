import { describe, expect, it } from "vitest";
import { PRIVACY_POLICY, TERMS_OF_SERVICE } from "@/content/legal";
import { selectedAddress, type DaumPostcodeData } from "@/lib/daum-postcode";
import { signupSchema } from "./schemas";

describe("signupSchema", () => {
  const valid = {
    loginId: "cameluser",
    email: "user@example.com",
    password: "StrongPassword1!",
    passwordConfirm: "StrongPassword1!",
    name: "홍길동",
    birthDate: "1990-01-01",
    gender: "FEMALE" as const,
    phone: "01012345678",
    postcode: "30100",
    address1: "세종특별자치시",
    address2: "101동",
    termsAgreed: true as const,
    privacyAgreed: true as const,
  };

  it("accepts valid signup payload", () => {
    expect(signupSchema.safeParse(valid).success).toBe(true);
  });

  it("accepts signup without email", () => {
    expect(signupSchema.safeParse({ ...valid, email: "" }).success).toBe(true);
  });

  it("rejects invalid login id", () => {
    const result = signupSchema.safeParse({ ...valid, loginId: "ab" });
    expect(result.success).toBe(false);
  });

  it("rejects weak password", () => {
    const result = signupSchema.safeParse({ ...valid, password: "short", passwordConfirm: "short" });
    expect(result.success).toBe(false);
  });

  it("rejects password mismatch", () => {
    const result = signupSchema.safeParse({
      ...valid,
      passwordConfirm: "StrongPassword2!",
    });
    expect(result.success).toBe(false);
  });

  it("rejects missing terms agreement", () => {
    const result = signupSchema.safeParse({ ...valid, termsAgreed: false });
    expect(result.success).toBe(false);
  });
});

describe("legal documents", () => {
  it("includes generic terms of service articles", () => {
    expect(TERMS_OF_SERVICE).toContain("제1조 (목적)");
    expect(TERMS_OF_SERVICE).toContain("제4조 (회원가입)");
    expect(TERMS_OF_SERVICE).toContain("제10조 (청약철회 및 환불)");
  });

  it("includes privacy policy sections", () => {
    expect(PRIVACY_POLICY).toContain("수집하는 개인정보 항목");
    expect(PRIVACY_POLICY).toContain("개인정보의 수집·이용 목적");
    expect(PRIVACY_POLICY).toContain("이용자의 권리");
  });
});

describe("selectedAddress", () => {
  it("uses road address and extra building info", () => {
    const data = {
      zonecode: "13529",
      address: "경기 성남시 분당구 판교역로 166",
      addressType: "R",
      userSelectedType: "R",
      roadAddress: "경기 성남시 분당구 판교역로 166",
      jibunAddress: "경기 성남시 분당구 백현동 532",
      bname: "백현동",
      buildingName: "카카오 판교 아지트",
      apartment: "Y",
    } satisfies DaumPostcodeData;

    expect(selectedAddress(data)).toBe(
      "경기 성남시 분당구 판교역로 166 (백현동, 카카오 판교 아지트)",
    );
  });
});
