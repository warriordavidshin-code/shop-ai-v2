import { z } from "zod";
import { parseApiError, shopFetch } from "@/lib/api/client";
import { DEFAULT_SHIPPING_POLICY, type ShippingPolicy } from "@/features/orders/delivery";

export const shippingPolicySchema = z.object({
  baseShippingFee: z.coerce.number(),
  freeShippingAmount: z.coerce.number(),
  jejuExtraFee: z.coerce.number(),
  remoteAreaExtraFee: z.coerce.number(),
  returnShippingFee: z.coerce.number(),
  exchangeShippingFee: z.coerce.number(),
  updatedAt: z.string().nullable().optional(),
});

export const shippingQuoteSchema = z.object({
  productAmount: z.coerce.number(),
  baseFee: z.coerce.number(),
  extraFee: z.coerce.number(),
  deliveryFee: z.coerce.number(),
  freeShipping: z.boolean(),
  areaType: z.enum(["JEJU", "REMOTE"]).nullable().optional(),
  freeShippingAmount: z.coerce.number(),
});

export type ShippingQuote = z.infer<typeof shippingQuoteSchema>;

export const trackingEventSchema = z.object({
  time: z.string().nullable().optional(),
  timestamp: z.string().nullable().optional(),
  location: z.string().nullable().optional(),
  description: z.string(),
  status: z.string().nullable().optional(),
  providerStatus: z.string().nullable().optional(),
  source: z.string().optional(),
});

export type TrackingEvent = z.infer<typeof trackingEventSchema>;

export const trackingSchema = z.object({
  orderId: z.number(),
  orderNo: z.string(),
  orderStatus: z.string(),
  shipmentType: z.string(),
  deliveryCompany: z.string().nullable().optional(),
  deliveryCompanyName: z.string().nullable().optional(),
  trackingNumber: z.string().nullable().optional(),
  trackingUrl: z.string().nullable().optional(),
  status: z.string().nullable().optional(),
  statusName: z.string().nullable().optional(),
  shippedAt: z.string().nullable().optional(),
  deliveredAt: z.string().nullable().optional(),
  lastCheckedAt: z.string().nullable().optional(),
  externalTracking: z.boolean(),
  message: z.string().nullable().optional(),
  events: z.array(trackingEventSchema).default([]),
});

export type Tracking = z.infer<typeof trackingSchema>;

export const returnRequestSchema = z.object({
  returnRequestId: z.number(),
  orderId: z.number(),
  reason: z.string(),
  reasonLabel: z.string(),
  reasonText: z.string().nullable().optional(),
  customerMemo: z.string().nullable().optional(),
  status: z.string(),
  statusName: z.string(),
  pickupName: z.string().nullable().optional(),
  pickupPhone: z.string().nullable().optional(),
  pickupPostcode: z.string().nullable().optional(),
  pickupAddress1: z.string().nullable().optional(),
  pickupAddress2: z.string().nullable().optional(),
  pickupDeliveryCompany: z.string().nullable().optional(),
  pickupDeliveryCompanyName: z.string().nullable().optional(),
  pickupTrackingNumber: z.string().nullable().optional(),
  pickupTrackingUrl: z.string().nullable().optional(),
  shipmentStatus: z.string().nullable().optional(),
  shipmentStatusName: z.string().nullable().optional(),
  freeReturn: z.boolean(),
  returnShippingFee: z.coerce.number(),
  refundAmount: z.coerce.number().nullable().optional(),
  restocked: z.boolean().optional(),
  rejectReason: z.string().nullable().optional(),
  requestedAt: z.string().nullable().optional(),
  approvedAt: z.string().nullable().optional(),
  pickupRequestedAt: z.string().nullable().optional(),
  pickedUpAt: z.string().nullable().optional(),
  receivedAt: z.string().nullable().optional(),
  completedAt: z.string().nullable().optional(),
  rejectedAt: z.string().nullable().optional(),
  cancelledAt: z.string().nullable().optional(),
});

export type ReturnRequest = z.infer<typeof returnRequestSchema>;

export const returnInfoSchema = z.object({
  returnRequest: returnRequestSchema.nullable().optional(),
  canRequest: z.boolean(),
  canCancel: z.boolean().default(false),
  returnShippingFee: z.coerce.number(),
  reasons: z.array(z.object({ code: z.string(), label: z.string(), freeReturn: z.boolean() })),
});

export type ReturnInfo = z.infer<typeof returnInfoSchema>;

export type ReturnInput = {
  returnReason: string;
  returnMemo?: string;
  customerMemo?: string;
  pickupName: string;
  pickupPhone: string;
  pickupPostcode: string;
  pickupAddress: string;
  pickupAddressDetail?: string;
};

export async function getShippingPolicy(): Promise<ShippingPolicy> {
  const response = await shopFetch("/shipping/policy");
  if (!response.ok) throw await parseApiError(response);
  return shippingPolicySchema.parse(await response.json());
}

/** Never throws: falls back to the default policy so price summaries always render. */
export async function getShippingPolicyOrDefault(): Promise<ShippingPolicy> {
  try {
    return await getShippingPolicy();
  } catch {
    return DEFAULT_SHIPPING_POLICY;
  }
}

export async function getShippingQuote(amount: number, postcode?: string) {
  const params = new URLSearchParams({ amount: String(amount) });
  if (postcode) params.set("postcode", postcode);
  const response = await shopFetch(`/shipping/quote?${params.toString()}`);
  if (!response.ok) throw await parseApiError(response);
  return shippingQuoteSchema.parse(await response.json());
}

export async function getTracking(orderId: number, type: "DELIVERY" | "RETURN" = "DELIVERY") {
  const response = await shopFetch(`/orders/${orderId}/tracking?type=${type}`);
  if (!response.ok) throw await parseApiError(response);
  return trackingSchema.parse(await response.json());
}

export async function getReturnInfo(orderId: number) {
  const response = await shopFetch(`/orders/${orderId}/return`);
  if (!response.ok) throw await parseApiError(response);
  return returnInfoSchema.parse(await response.json());
}

export async function requestReturn(orderId: number, input: ReturnInput) {
  const response = await shopFetch(`/orders/${orderId}/return`, {
    method: "POST",
    body: JSON.stringify(input),
  });
  if (!response.ok) throw await parseApiError(response);
  return returnRequestSchema.parse(await response.json());
}

/** Withdraws the order's return while it is still 반품신청 / 반품승인. */
export async function cancelReturn(orderId: number) {
  const response = await shopFetch(`/orders/${orderId}/return/cancel`, { method: "POST" });
  if (!response.ok) throw await parseApiError(response);
  return returnRequestSchema.parse(await response.json());
}
