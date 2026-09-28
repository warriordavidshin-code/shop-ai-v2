import { z } from "zod";
import { parseApiError, shopFetch } from "@/lib/api/client";

export const adminCancelRequestSchema = z.object({
  cancelRequestId: z.number(),
  orderId: z.number(),
  orderNo: z.string(),
  orderStatus: z.string(),
  memberId: z.number(),
  memberLoginId: z.string().nullable().optional(),
  memberName: z.string().nullable().optional(),
  status: z.enum(["REQUESTED", "APPROVED", "REJECTED"]),
  reason: z.string(),
  previousOrderStatus: z.string(),
  rejectReason: z.string().nullable().optional(),
  paymentAmount: z.coerce.number(),
  itemSummary: z.string().nullable().optional(),
  requestedAt: z.string().nullable().optional(),
  processedAt: z.string().nullable().optional(),
});

export type AdminCancelRequest = z.infer<typeof adminCancelRequestSchema>;

const pageSchema = z.object({
  content: z.array(adminCancelRequestSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});

export type CancelRequestFilter = "REQUESTED" | "APPROVED" | "REJECTED" | "ALL";

export async function listCancelRequests(filter: CancelRequestFilter, page = 0, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (filter !== "ALL") params.set("status", filter);
  const response = await shopFetch(`/admin/order-cancel-requests?${params.toString()}`);
  if (!response.ok) throw await parseApiError(response);
  return pageSchema.parse(await response.json());
}

export async function approveCancelRequest(id: number) {
  const response = await shopFetch(`/admin/order-cancel-requests/${id}/approve`, { method: "POST" });
  if (!response.ok) throw await parseApiError(response);
  return adminCancelRequestSchema.parse(await response.json());
}

export async function rejectCancelRequest(id: number, reason: string) {
  const response = await shopFetch(`/admin/order-cancel-requests/${id}/reject`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
  if (!response.ok) throw await parseApiError(response);
  return adminCancelRequestSchema.parse(await response.json());
}
