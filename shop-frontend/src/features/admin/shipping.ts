import { z } from "zod";
import { parseApiError, shopFetch } from "@/lib/api/client";
import { orderSchema } from "@/features/orders/api";
import {
  returnRequestSchema,
  shippingPolicySchema,
  trackingEventSchema,
  type Tracking,
} from "@/features/shipping/api";
import type { InvoiceRow } from "@/features/shipping/csv";

const nullableString = z.string().nullable().optional();

export const shipmentViewSchema = z.object({
  shipmentId: z.number(),
  orderId: z.number(),
  shipmentType: z.string(),
  status: z.string(),
  statusName: z.string(),
  deliveryCompany: nullableString,
  deliveryCompanyName: nullableString,
  trackingNumber: nullableString,
  trackingUrl: nullableString,
  pickupRequestedAt: nullableString,
  pickedUpAt: nullableString,
  shippedAt: nullableString,
  deliveredAt: nullableString,
  lastTrackingCheckedAt: nullableString,
  lastTrackingError: nullableString,
  events: z.array(trackingEventSchema).nullable().optional(),
});

export type ShipmentView = z.infer<typeof shipmentViewSchema>;

const shipmentActionSchema = z.object({
  success: z.boolean(),
  message: z.string(),
  shipment: shipmentViewSchema.nullable().optional(),
});

export type ShipmentAction = z.infer<typeof shipmentActionSchema>;

export const adminOrderRowSchema = z.object({
  orderId: z.number(),
  orderNo: z.string(),
  memberId: z.number().nullable().optional(),
  memberLoginId: nullableString,
  memberName: nullableString,
  receiverName: nullableString,
  itemSummary: nullableString,
  orderStatus: z.string(),
  paymentAmount: z.coerce.number(),
  orderedAt: nullableString,
  itemCount: z.number().optional(),
  shipmentStatus: nullableString,
  shipmentStatusName: nullableString,
  deliveryCompany: nullableString,
  deliveryCompanyName: nullableString,
  trackingNumber: nullableString,
  trackingUrl: nullableString,
  pickupStatus: nullableString,
  returnStatus: nullableString,
  returnStatusName: nullableString,
});

export type AdminOrderRow = z.infer<typeof adminOrderRowSchema>;

function pageOf<T extends z.ZodTypeAny>(item: T) {
  return z.object({
    content: z.array(item),
    page: z.number(),
    size: z.number(),
    totalElements: z.number(),
    totalPages: z.number(),
  });
}

export const adminOrderDetailSchema = z.object({
  order: orderSchema,
  memberLoginId: nullableString,
  memberName: nullableString,
  delivery: shipmentViewSchema.nullable().optional(),
  returnShipment: shipmentViewSchema.nullable().optional(),
  returnRequest: returnRequestSchema.nullable().optional(),
});

export type AdminOrderDetail = z.infer<typeof adminOrderDetailSchema>;

export const deliveryCompanySchema = z.object({
  code: z.string(),
  companyName: z.string(),
  trackingUrlTemplate: nullableString,
  enabled: z.boolean(),
  sortOrder: z.number(),
  providerCodes: z.record(z.string(), z.string()).default({}),
});

export type DeliveryCompany = z.infer<typeof deliveryCompanySchema>;

export const bulkInvoiceResultSchema = z.object({
  total: z.number(),
  successCount: z.number(),
  failureCount: z.number(),
  results: z.array(
    z.object({
      row: z.number(),
      orderNumber: nullableString,
      success: z.boolean(),
      message: z.string(),
    }),
  ),
});

export type BulkInvoiceResult = z.infer<typeof bulkInvoiceResultSchema>;

export const adminReturnSchema = z.object({
  returnRequest: returnRequestSchema,
  orderNo: z.string(),
  orderStatus: z.string(),
  memberId: z.number().nullable().optional(),
  memberLoginId: nullableString,
  memberName: nullableString,
  itemSummary: nullableString,
  paymentAmount: z.coerce.number(),
  shipmentStatus: nullableString,
  shipmentStatusName: nullableString,
});

export type AdminReturn = z.infer<typeof adminReturnSchema>;

export const extraAreaSchema = z.object({
  areaId: z.number(),
  areaType: z.enum(["JEJU", "REMOTE"]),
  postcodeFrom: z.string(),
  postcodeTo: z.string(),
  note: nullableString,
});

export type ExtraArea = z.infer<typeof extraAreaSchema>;

export const integrationSchema = z.object({
  requestedProvider: z.string(),
  activeProvider: z.string(),
  externalTracking: z.boolean(),
  apiKeyConfigured: z.boolean(),
  pickupService: z.string(),
  waybillSupported: z.boolean(),
  schedulerEnabled: z.boolean(),
  schedulerCron: z.string(),
  cacheMinutes: z.number(),
});

export type ShippingIntegration = z.infer<typeof integrationSchema>;

/** Order list tabs; `view` values match AdminOrderQueryService.View. */
export const ORDER_VIEWS = [
  { value: "ALL", label: "전체" },
  { value: "TODAY", label: "오늘 주문" },
  { value: "READY", label: "배송 준비" },
  { value: "SHIPPING", label: "배송중" },
  { value: "DELIVERED", label: "배송완료" },
  { value: "RETURN", label: "반품" },
  { value: "CANCEL", label: "취소" },
] as const;

export type OrderView = (typeof ORDER_VIEWS)[number]["value"];

export function toOrderView(value: string | null | undefined): OrderView {
  return ORDER_VIEWS.some((v) => v.value === value) ? (value as OrderView) : "ALL";
}

/** Manual delivery status targets offered on the order detail page (forward-only on the server). */
export const MANUAL_SHIPMENT_TARGETS = [
  { value: "READY", label: "배송준비" },
  { value: "PICKED_UP", label: "집하완료" },
  { value: "IN_TRANSIT", label: "배송중" },
  { value: "OUT_FOR_DELIVERY", label: "배송출발" },
  { value: "DELIVERED", label: "배송완료" },
] as const;

/** Adapts an admin ShipmentView to the customer tracking shape so TrackingModal can render it. */
export function shipmentToTracking(
  shipment: ShipmentView | null | undefined,
  order: { orderId: number; orderNo: string; orderStatus: string },
): Tracking | null {
  if (!shipment) return null;
  return {
    orderId: order.orderId,
    orderNo: order.orderNo,
    orderStatus: order.orderStatus,
    shipmentType: shipment.shipmentType,
    deliveryCompany: shipment.deliveryCompany,
    deliveryCompanyName: shipment.deliveryCompanyName,
    trackingNumber: shipment.trackingNumber,
    trackingUrl: shipment.trackingUrl,
    status: shipment.status,
    statusName: shipment.statusName,
    shippedAt: shipment.shippedAt,
    deliveredAt: shipment.deliveredAt,
    lastCheckedAt: shipment.lastTrackingCheckedAt,
    externalTracking: true,
    message: shipment.lastTrackingError ? "배송정보를 일시적으로 조회할 수 없습니다." : null,
    events: shipment.events ?? [],
  };
}

async function send<T extends z.ZodTypeAny>(schema: T, path: string, init?: RequestInit): Promise<z.infer<T>> {
  const response = await shopFetch(path, init);
  if (!response.ok) throw await parseApiError(response);
  return schema.parse(await response.json());
}

function json(method: string, body?: unknown): RequestInit {
  return body === undefined ? { method } : { method, body: JSON.stringify(body) };
}

// ----- orders / delivery -----

export function listAdminOrders(params: { view?: OrderView; keyword?: string; page?: number; size?: number }) {
  const query = new URLSearchParams({ page: String(params.page ?? 0), size: String(params.size ?? 20) });
  if (params.view && params.view !== "ALL") query.set("view", params.view);
  if (params.keyword?.trim()) query.set("keyword", params.keyword.trim());
  return send(pageOf(adminOrderRowSchema), `/admin/orders?${query.toString()}`);
}

export function getAdminOrder(orderNo: string) {
  return send(adminOrderDetailSchema, `/admin/orders/${encodeURIComponent(orderNo)}`);
}

export function registerInvoice(orderId: number, deliveryCompany: string, trackingNumber: string) {
  return send(shipmentActionSchema, `/admin/orders/${orderId}/shipment`, json("PUT", { deliveryCompany, trackingNumber }));
}

export function changeShipmentStatus(orderId: number, status: string) {
  return send(shipmentActionSchema, `/admin/orders/${orderId}/shipment/status`, json("PATCH", { status }));
}

export function requestDeliveryPickup(orderId: number, deliveryCompany?: string) {
  return send(
    shipmentActionSchema,
    `/admin/orders/${orderId}/shipment/pickup-request`,
    json("POST", { deliveryCompany: deliveryCompany || null }),
  );
}

export function refreshTracking(orderId: number, type: "DELIVERY" | "RETURN" = "DELIVERY") {
  return send(shipmentActionSchema, `/admin/orders/${orderId}/shipment/refresh?type=${type}`, json("POST"));
}

export function issueWaybill(orderId: number) {
  return send(z.unknown(), `/admin/orders/${orderId}/shipment/waybill`, json("POST"));
}

export function printWaybill(orderId: number) {
  return send(z.unknown(), `/admin/orders/${orderId}/shipment/waybill/print`, json("POST"));
}

export function bulkRegisterInvoices(items: InvoiceRow[]) {
  return send(bulkInvoiceResultSchema, "/admin/shipments/bulk", json("POST", { items }));
}

// ----- returns -----

export const RETURN_FILTERS = [
  { value: "OPEN", label: "처리 대기" },
  { value: "REQUESTED", label: "반품신청" },
  { value: "APPROVED", label: "반품승인" },
  { value: "PICKUP_REQUESTED", label: "수거요청" },
  { value: "RECEIVED", label: "반품입고" },
  { value: "REFUNDED", label: "환불완료" },
  { value: "REJECTED", label: "거절" },
  { value: "ALL", label: "전체" },
] as const;

export type ReturnFilter = (typeof RETURN_FILTERS)[number]["value"];

export function toReturnFilter(value: string | null | undefined): ReturnFilter {
  return RETURN_FILTERS.some((f) => f.value === value) ? (value as ReturnFilter) : "OPEN";
}

export function listReturns(status: ReturnFilter, page = 0, size = 20) {
  const query = new URLSearchParams({ page: String(page), size: String(size), status });
  return send(pageOf(adminReturnSchema), `/admin/returns?${query.toString()}`);
}

export function approveReturn(id: number) {
  return send(adminReturnSchema, `/admin/returns/${id}/approve`, json("POST"));
}

export function rejectReturn(id: number, reason: string) {
  return send(adminReturnSchema, `/admin/returns/${id}/reject`, json("POST", { reason }));
}

export function requestReturnPickup(id: number, deliveryCompany?: string, trackingNumber?: string) {
  return send(
    adminReturnSchema,
    `/admin/returns/${id}/pickup-request`,
    json("POST", { deliveryCompany: deliveryCompany || null, trackingNumber: trackingNumber || null }),
  );
}

export function registerReturnTracking(id: number, deliveryCompany: string, trackingNumber: string) {
  return send(adminReturnSchema, `/admin/returns/${id}/tracking`, json("PUT", { deliveryCompany, trackingNumber }));
}

export function changeReturnStatus(id: number, status: "PICKED_UP" | "IN_TRANSIT" | "RECEIVED") {
  return send(adminReturnSchema, `/admin/returns/${id}/status`, json("PATCH", { status }));
}

export function refundReturn(id: number, restock: boolean, adminMemo?: string) {
  return send(adminReturnSchema, `/admin/returns/${id}/refund`, json("POST", { restock, adminMemo: adminMemo || null }));
}

// ----- settings -----

export function listDeliveryCompanies() {
  return send(z.array(deliveryCompanySchema), "/admin/shipping/delivery-companies");
}

export function setDeliveryCompanyEnabled(code: string, enabled: boolean) {
  return send(deliveryCompanySchema, `/admin/shipping/delivery-companies/${encodeURIComponent(code)}`, json("PATCH", { enabled }));
}

export function getAdminShippingPolicy() {
  return send(shippingPolicySchema, "/admin/shipping/policy");
}

export type ShippingPolicyInput = {
  baseShippingFee: number;
  freeShippingAmount: number;
  jejuExtraFee: number;
  remoteAreaExtraFee: number;
  returnShippingFee: number;
};

export function updateShippingPolicy(input: ShippingPolicyInput) {
  return send(shippingPolicySchema, "/admin/shipping/policy", json("PUT", input));
}

export function listExtraAreas() {
  return send(z.array(extraAreaSchema), "/admin/shipping/extra-areas");
}

export function addExtraArea(input: { areaType: "JEJU" | "REMOTE"; postcodeFrom: string; postcodeTo: string; note?: string }) {
  return send(extraAreaSchema, "/admin/shipping/extra-areas", json("POST", input));
}

export async function deleteExtraArea(areaId: number) {
  const response = await shopFetch(`/admin/shipping/extra-areas/${areaId}`, { method: "DELETE" });
  if (!response.ok) throw await parseApiError(response);
}

export function getShippingIntegration() {
  return send(integrationSchema, "/admin/shipping/integration");
}
