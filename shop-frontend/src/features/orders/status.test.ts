import { describe, expect, it } from "vitest";
import {
  canCancelImmediately,
  canRequestCancel,
  cancelRequestStatusLabel,
  formatDateTime,
  orderStatusLabel,
} from "./status";

describe("order status helpers", () => {
  it("labels known statuses in Korean and passes unknown ones through", () => {
    expect(orderStatusLabel("PAID")).toBe("결제 완료");
    expect(orderStatusLabel("CANCEL_REQUESTED")).toBe("취소 요청중");
    expect(orderStatusLabel("SOMETHING_NEW")).toBe("SOMETHING_NEW");
    expect(cancelRequestStatusLabel("REJECTED")).toBe("취소 거절");
  });

  it("allows immediate cancel only before payment", () => {
    expect(canCancelImmediately("PAYMENT_PENDING")).toBe(true);
    expect(canCancelImmediately("PAID")).toBe(false);
  });

  it("allows cancel requests only for paid orders that have not shipped", () => {
    expect(canRequestCancel("PAID")).toBe(true);
    expect(canRequestCancel("PREPARING")).toBe(true);
    expect(canRequestCancel("SHIPPED")).toBe(false);
    expect(canRequestCancel("CANCEL_REQUESTED")).toBe(false);
    expect(canRequestCancel("PAYMENT_PENDING")).toBe(false);
  });

  it("formats timestamps in Korea time and tolerates missing values", () => {
    expect(formatDateTime(null)).toBe("-");
    expect(formatDateTime("not-a-date")).toBe("-");
    expect(formatDateTime("2026-09-28T00:30:00Z")).toContain("09:30");
  });
});
