import { formatKrw } from "@/lib/format";

/** Mirrors the backend DeliveryFeePolicy; the server remains the source of truth for charged amounts. */
export const DELIVERY_FEE = 3000;
export const FREE_SHIPPING_THRESHOLD = 50000;

export const FREE_SHIPPING_NOTICE = `${formatKrw(FREE_SHIPPING_THRESHOLD)}원 이상 결제 시 무료배송`;

export function deliveryFeeFor(productAmount: number): number {
  if (!(productAmount > 0)) return 0;
  return productAmount >= FREE_SHIPPING_THRESHOLD ? 0 : DELIVERY_FEE;
}

export function amountUntilFreeShipping(productAmount: number): number {
  if (!(productAmount > 0)) return FREE_SHIPPING_THRESHOLD;
  return Math.max(FREE_SHIPPING_THRESHOLD - productAmount, 0);
}

/** Customer-facing message for the order summary box. */
export function freeShippingMessage(productAmount: number): string {
  const remaining = amountUntilFreeShipping(productAmount);
  if (productAmount > 0 && remaining === 0) {
    return `${FREE_SHIPPING_NOTICE}이 적용되었습니다.`;
  }
  return `${formatKrw(remaining)}원 더 구매하시면 무료배송입니다. (${FREE_SHIPPING_NOTICE})`;
}
