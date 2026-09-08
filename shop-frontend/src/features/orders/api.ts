import { z } from "zod";
import { parseApiError, shopFetch } from "@/lib/api/client";

export const orderItemSchema = z.object({
  orderItemId: z.number().optional(),
  productId: z.number().optional(),
  skuId: z.number(),
  productName: z.string(),
  optionName: z.string(),
  quantity: z.number(),
  unitPrice: z.coerce.number(),
  discountPrice: z.coerce.number().optional(),
  paymentPrice: z.coerce.number().optional(),
});

export const orderSchema = z.object({
  orderId: z.number().optional(),
  orderNo: z.string(),
  orderStatus: z.string(),
  totalProductAmount: z.coerce.number(),
  discountAmount: z.coerce.number(),
  deliveryAmount: z.coerce.number(),
  paymentAmount: z.coerce.number(),
  receiverName: z.string(),
  receiverPhone: z.string(),
  postcode: z.string(),
  address1: z.string(),
  address2: z.string().nullable().optional(),
  orderMemo: z.string().nullable().optional(),
  orderedAt: z.string().optional(),
  items: z.array(orderItemSchema).default([]),
  paymentStatus: z.string().optional(),
  paymentMethod: z.string().optional(),
});

export type Order = z.infer<typeof orderSchema>;

export type CreateOrderInput = {
  items: { skuId: number; quantity: number }[];
  receiverName: string;
  receiverPhone: string;
  postcode: string;
  address1: string;
  address2?: string;
  orderMemo?: string;
};

export async function createOrder(input: CreateOrderInput, idempotencyKey: string) {
  const response = await shopFetch("/orders", {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(input),
  });
  if (!response.ok) throw await parseApiError(response);
  return orderSchema.parse(await response.json());
}

export async function getOrder(orderNo: string) {
  const response = await shopFetch(`/orders/${orderNo}`);
  if (!response.ok) throw await parseApiError(response);
  return orderSchema.parse(await response.json());
}

export async function listMyOrders(page = 0, size = 20) {
  const response = await shopFetch(`/members/me/orders?page=${page}&size=${size}`);
  if (!response.ok) throw await parseApiError(response);
  const data = await response.json();
  return z
    .object({
      content: z.array(
        z.object({
          orderId: z.number().optional(),
          orderNo: z.string(),
          orderStatus: z.string(),
          paymentAmount: z.coerce.number(),
          orderedAt: z.string().optional(),
          itemCount: z.number().optional(),
        }),
      ),
      page: z.number(),
      size: z.number(),
      totalElements: z.number(),
      totalPages: z.number(),
    })
    .parse(data);
}

export async function mockApprovePayment(orderNo: string) {
  const response = await shopFetch("/payments/mock/approve", {
    method: "POST",
    body: JSON.stringify({ orderNo }),
  });
  if (!response.ok) throw await parseApiError(response);
  return response.json();
}

export async function cancelOrder(orderNo: string) {
  const response = await shopFetch(`/orders/${orderNo}/cancel`, { method: "POST" });
  if (!response.ok) throw await parseApiError(response);
  return orderSchema.parse(await response.json());
}
