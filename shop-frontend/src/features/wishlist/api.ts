import { z } from "zod";
import { parseApiError, shopFetch } from "@/lib/api/client";
import { productSummarySchema, type ProductSummary } from "@/features/products/api";

export async function getWishlist(): Promise<ProductSummary[]> {
  const response = await shopFetch("/wishlist");
  if (!response.ok) throw await parseApiError(response);
  return z.array(productSummarySchema).parse(await response.json());
}

export async function addWishlist(productId: number) {
  const response = await shopFetch(`/wishlist/${productId}`, { method: "POST" });
  if (!response.ok && response.status !== 204) throw await parseApiError(response);
}

export async function removeWishlist(productId: number) {
  const response = await shopFetch(`/wishlist/${productId}`, { method: "DELETE" });
  if (!response.ok && response.status !== 204) throw await parseApiError(response);
}
