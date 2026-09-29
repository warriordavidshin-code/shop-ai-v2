import { z } from "zod";
import { parseApiError, shopFetch } from "@/lib/api/client";

export const adminSkuSchema = z.object({
  skuId: z.number(),
  skuCode: z.string(),
  color: z.string(),
  size: z.string(),
  status: z.string(),
  stockQuantity: z.number(),
  reservedQuantity: z.number(),
  availableQuantity: z.number(),
});

export type AdminSku = z.infer<typeof adminSkuSchema>;

export const adminProductSchema = z.object({
  productId: z.number(),
  productName: z.string(),
  brandName: z.string().nullable().optional(),
  status: z.string(),
  normalPrice: z.coerce.number(),
  salePrice: z.coerce.number(),
  discountRate: z.number(),
  skus: z.array(adminSkuSchema).default([]),
});

export type AdminProduct = z.infer<typeof adminProductSchema>;

export const adminProductPageSchema = z.object({
  content: z.array(adminProductSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});

export type AdminProductPage = z.infer<typeof adminProductPageSchema>;

const inventorySchema = z.object({
  skuId: z.number(),
  stockQuantity: z.number(),
  reservedQuantity: z.number(),
  availableQuantity: z.number(),
});

export const MAX_STOCK_QUANTITY = 1_000_000;

/** Returns the parsed quantity, or null when the input is not a whole number in range. */
export function parseStockInput(value: string): number | null {
  const text = value.trim();
  if (!/^\d+$/.test(text)) return null;
  const n = Number(text);
  return n <= MAX_STOCK_QUANTITY ? n : null;
}

export function stockSummary(skus: Pick<AdminSku, "stockQuantity" | "reservedQuantity" | "availableQuantity">[]) {
  return skus.reduce(
    (acc, sku) => ({
      stock: acc.stock + sku.stockQuantity,
      reserved: acc.reserved + sku.reservedQuantity,
      available: acc.available + sku.availableQuantity,
    }),
    { stock: 0, reserved: 0, available: 0 },
  );
}

function codePart(value: string): string {
  const cleaned = value
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9가-힣]+/g, "-")
    .replace(/^-+|-+$/g, "");
  return cleaned || "X";
}

export function buildSkuCode(productId: number, color: string, size: string): string {
  return `P${productId}-${codePart(color)}-${codePart(size)}`.slice(0, 64);
}

export async function setSkuStock(skuId: number, stockQuantity: number) {
  const response = await shopFetch(`/admin/inventories/${skuId}/stock`, {
    method: "PUT",
    body: JSON.stringify({ stockQuantity }),
  });
  if (!response.ok) throw await parseApiError(response);
  return inventorySchema.parse(await response.json());
}

export async function addProductSku(
  productId: number,
  input: { color: string; size: string; stockQuantity: number },
) {
  const response = await shopFetch(`/admin/products/${productId}/skus`, {
    method: "POST",
    body: JSON.stringify({
      skuCode: buildSkuCode(productId, input.color, input.size),
      color: input.color.trim(),
      size: input.size.trim(),
      stockQuantity: input.stockQuantity,
    }),
  });
  if (!response.ok) throw await parseApiError(response);
  return adminProductSchema.parse(await response.json());
}
