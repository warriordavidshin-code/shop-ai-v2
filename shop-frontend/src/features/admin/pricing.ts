function toAmount(value: number | string): number {
  if (typeof value === "number") return value;
  const text = value.replace(/,/g, "").trim();
  return text === "" ? NaN : Number(text);
}

export function clampDiscountRate(rate: number): number {
  if (!Number.isFinite(rate)) return 0;
  return Math.min(100, Math.max(0, rate));
}

/** Sale price = normal × (100 − rate) / 100, rounded to the nearest won. */
export function salePriceFromRate(normalPrice: number | string, rate: number | string): number | null {
  const normal = toAmount(normalPrice);
  const r = toAmount(rate);
  if (!Number.isFinite(normal) || normal < 0 || !Number.isFinite(r)) return null;
  return Math.round((normal * (100 - clampDiscountRate(r))) / 100);
}

/** Same rounding as the backend ProductPricing.discountRate. */
export function discountRateFrom(normalPrice: number | string, salePrice: number | string): number | null {
  const normal = toAmount(normalPrice);
  const sale = toAmount(salePrice);
  if (!Number.isFinite(normal) || !Number.isFinite(sale)) return null;
  if (normal <= 0) return 0;
  return clampDiscountRate(Math.round(((normal - sale) / normal) * 100));
}

export function validatePrices(normalPrice: number | string, salePrice: number | string): string | null {
  const normal = toAmount(normalPrice);
  const sale = toAmount(salePrice);
  if (!Number.isFinite(normal) || normal < 0) return "정상가를 0 이상의 숫자로 입력해 주세요.";
  if (!Number.isFinite(sale) || sale < 0) return "판매가를 0 이상의 숫자로 입력해 주세요.";
  if (sale > normal) return "판매가는 정상가보다 클 수 없습니다.";
  return null;
}
