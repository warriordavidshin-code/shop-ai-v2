import { z } from "zod";
import { parseApiError, shopFetch } from "@/lib/api/client";

export const cartItemSchema = z.object({
  cartItemId: z.number(),
  skuId: z.number(),
  productId: z.number().optional(),
  productName: z.string(),
  optionName: z.string(),
  unitPrice: z.coerce.number(),
  quantity: z.number(),
  availableQuantity: z.number(),
  lineTotal: z.coerce.number(),
});

export const cartSchema = z.object({
  items: z.array(cartItemSchema),
  productAmount: z.coerce.number(),
  deliveryAmount: z.coerce.number(),
  paymentAmount: z.coerce.number(),
});

export type Cart = z.infer<typeof cartSchema>;

const GUEST_CART_KEY = "boutiquecamel_guest_cart";

export type GuestCartItem = { skuId: number; quantity: number };

export function readGuestCart(): GuestCartItem[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = localStorage.getItem(GUEST_CART_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as GuestCartItem[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function writeGuestCart(items: GuestCartItem[]) {
  localStorage.setItem(GUEST_CART_KEY, JSON.stringify(items));
}

export function clearGuestCart() {
  localStorage.removeItem(GUEST_CART_KEY);
}

export async function getCart(): Promise<Cart> {
  const response = await shopFetch("/cart");
  if (!response.ok) throw await parseApiError(response);
  return cartSchema.parse(await response.json());
}

export async function addCartItem(skuId: number, quantity: number) {
  const response = await shopFetch("/cart/items", {
    method: "POST",
    body: JSON.stringify({ skuId, quantity }),
  });
  if (!response.ok) throw await parseApiError(response);
  return cartSchema.parse(await response.json());
}

export async function updateCartItem(cartItemId: number, quantity: number) {
  const response = await shopFetch(`/cart/items/${cartItemId}`, {
    method: "PATCH",
    body: JSON.stringify({ quantity }),
  });
  if (!response.ok) throw await parseApiError(response);
  return cartSchema.parse(await response.json());
}

export async function removeCartItem(cartItemId: number) {
  const response = await shopFetch(`/cart/items/${cartItemId}`, { method: "DELETE" });
  if (!response.ok && response.status !== 204) throw await parseApiError(response);
}

export async function mergeGuestCart() {
  const items = readGuestCart();
  if (items.length === 0) return null;
  const response = await shopFetch("/cart/merge", {
    method: "POST",
    body: JSON.stringify({ items }),
  });
  if (!response.ok) throw await parseApiError(response);
  clearGuestCart();
  return cartSchema.parse(await response.json());
}

export function cartTotals(items: { unitPrice: number; quantity: number }[]) {
  const productAmount = items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);
  const deliveryAmount = productAmount > 0 ? 3000 : 0;
  return {
    productAmount,
    deliveryAmount,
    paymentAmount: productAmount + deliveryAmount,
  };
}
