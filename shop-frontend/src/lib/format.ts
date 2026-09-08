export function formatKrw(amount: number | string): string {
  const value = typeof amount === "string" ? Number(amount) : amount;
  if (!Number.isFinite(value)) {
    return "-";
  }
  return new Intl.NumberFormat("ko-KR").format(Math.round(value));
}

export function discountRate(normalPrice: number, salePrice: number): number {
  if (normalPrice <= 0 || salePrice >= normalPrice) {
    return 0;
  }
  return Math.round(((normalPrice - salePrice) / normalPrice) * 100);
}
