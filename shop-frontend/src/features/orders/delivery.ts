import { formatKrw } from "@/lib/format";

/** Admin-editable shipping policy (GET /api/shipping/policy). The server remains the source of truth. */
export type ShippingPolicy = {
  baseShippingFee: number;
  freeShippingAmount: number;
  jejuExtraFee: number;
  remoteAreaExtraFee: number;
  returnShippingFee: number;
};

/** Seed values of the shipping_policy table; used until the live policy loads. */
export const DEFAULT_SHIPPING_POLICY: ShippingPolicy = {
  baseShippingFee: 3000,
  freeShippingAmount: 50000,
  jejuExtraFee: 3000,
  remoteAreaExtraFee: 5000,
  returnShippingFee: 3000,
};

export const DELIVERY_FEE = DEFAULT_SHIPPING_POLICY.baseShippingFee;
export const FREE_SHIPPING_THRESHOLD = DEFAULT_SHIPPING_POLICY.freeShippingAmount;

export function freeShippingNotice(policy: ShippingPolicy = DEFAULT_SHIPPING_POLICY): string {
  return `${formatKrw(policy.freeShippingAmount)}원 이상 결제 시 무료배송`;
}

export const FREE_SHIPPING_NOTICE = freeShippingNotice();

export function deliveryFeeFor(productAmount: number, policy: ShippingPolicy = DEFAULT_SHIPPING_POLICY): number {
  if (!(productAmount > 0)) return 0;
  return productAmount >= policy.freeShippingAmount ? 0 : policy.baseShippingFee;
}

export function amountUntilFreeShipping(
  productAmount: number,
  policy: ShippingPolicy = DEFAULT_SHIPPING_POLICY,
): number {
  if (!(productAmount > 0)) return policy.freeShippingAmount;
  return Math.max(policy.freeShippingAmount - productAmount, 0);
}

/** Customer-facing message for the order summary box. */
export function freeShippingMessage(productAmount: number, policy: ShippingPolicy = DEFAULT_SHIPPING_POLICY): string {
  const remaining = amountUntilFreeShipping(productAmount, policy);
  const notice = freeShippingNotice(policy);
  if (productAmount > 0 && remaining === 0) {
    return `${notice}이 적용되었습니다.`;
  }
  return `${formatKrw(remaining)}원 더 구매하시면 무료배송입니다. (${notice})`;
}
