import { describe, expect, it } from "vitest";
import { signupSchema } from "./schemas";

describe("signupSchema", () => {
  const valid = {
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
});
