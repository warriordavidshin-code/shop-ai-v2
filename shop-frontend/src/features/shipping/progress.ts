/** Customer progress bar: 상품준비 → 집하완료 → 배송중 → 배송출발 → 배송완료. */
export const DELIVERY_STEPS = [
  { key: "READY", label: "상품준비" },
  { key: "PICKED_UP", label: "집하완료" },
  { key: "IN_TRANSIT", label: "배송중" },
  { key: "OUT_FOR_DELIVERY", label: "배송출발" },
  { key: "DELIVERED", label: "배송완료" },
] as const;

/** Return business flow (the physical pickup is shown by the return shipment's status). */
export const RETURN_STEPS = [
  { key: "REQUESTED", label: "반품신청" },
  { key: "APPROVED", label: "반품승인" },
  { key: "PICKUP_REQUESTED", label: "수거요청" },
  { key: "IN_PROGRESS", label: "반품배송중" },
  { key: "RECEIVED", label: "반품입고" },
  { key: "COMPLETED", label: "반품완료" },
] as const;

const SHIPMENT_STEP_INDEX: Record<string, number> = {
  READY: 0,
  WAYBILL_ISSUED: 0,
  PICKUP_REQUESTED: 0,
  PICKED_UP: 1,
  IN_TRANSIT: 2,
  OUT_FOR_DELIVERY: 3,
  DELIVERED: 4,
};

const ORDER_STEP_INDEX: Record<string, number> = {
  PAID: 0,
  PREPARING: 0,
  SHIPPED: 2,
  DELIVERED: 4,
  RETURN_REQUESTED: 4,
  RETURNED: 4,
};

/**
 * Index of the highlighted delivery step, or -1 when the order is not in the delivery flow
 * (unpaid, cancelled). Uses the shipment status when known, else the order status.
 */
export function deliveryStepIndex(orderStatus: string, shipmentStatus?: string | null): number {
  if (shipmentStatus && shipmentStatus in SHIPMENT_STEP_INDEX) {
    return SHIPMENT_STEP_INDEX[shipmentStatus];
  }
  return ORDER_STEP_INDEX[orderStatus] ?? -1;
}

export function returnStepIndex(returnStatus: string | null | undefined): number {
  if (!returnStatus) return -1;
  return RETURN_STEPS.findIndex((step) => step.key === returnStatus);
}

/** Customers may withdraw a return until the pickup is booked. */
export const CUSTOMER_CANCELLABLE_RETURN = new Set(["REQUESTED", "APPROVED"]);

export const RETURN_STATUS_LABELS: Record<string, string> = {
  REQUESTED: "반품신청",
  APPROVED: "반품승인",
  PICKUP_REQUESTED: "반품수거요청",
  IN_PROGRESS: "반품배송중",
  RECEIVED: "반품입고",
  COMPLETED: "반품완료",
  REJECTED: "반품거절",
  CANCELLED: "반품철회",
};

/** Labels of a delivery (outbound) shipment. Return shipments come with their own label from the API. */
export const SHIPMENT_STATUS_LABELS: Record<string, string> = {
  READY: "상품준비중",
  WAYBILL_ISSUED: "송장발급",
  PICKUP_REQUESTED: "집하요청",
  PICKED_UP: "집하완료",
  IN_TRANSIT: "배송중",
  OUT_FOR_DELIVERY: "배송출발",
  DELIVERED: "배송완료",
  CANCELLED: "배송취소",
  FAILED: "배송실패",
};
