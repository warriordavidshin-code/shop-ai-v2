/** Customer progress bar: 상품준비 → 집하완료 → 배송중 → 배송출발 → 배송완료. */
export const DELIVERY_STEPS = [
  { key: "PREPARING", label: "상품준비" },
  { key: "PICKED_UP", label: "집하완료" },
  { key: "IN_TRANSIT", label: "배송중" },
  { key: "OUT_FOR_DELIVERY", label: "배송출발" },
  { key: "DELIVERED", label: "배송완료" },
] as const;

export const RETURN_STEPS = [
  { key: "REQUESTED", label: "반품신청" },
  { key: "APPROVED", label: "반품승인" },
  { key: "PICKUP_REQUESTED", label: "수거요청" },
  { key: "PICKED_UP", label: "기사 방문수거" },
  { key: "IN_TRANSIT", label: "반품배송중" },
  { key: "RECEIVED", label: "반품입고" },
  { key: "REFUNDED", label: "환불완료" },
] as const;

const SHIPMENT_STEP_INDEX: Record<string, number> = {
  PREPARING: 0,
  READY: 0,
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

export const RETURN_STATUS_LABELS: Record<string, string> = {
  REQUESTED: "반품신청",
  APPROVED: "반품승인",
  PICKUP_REQUESTED: "반품수거요청",
  PICKED_UP: "기사방문수거",
  IN_TRANSIT: "반품배송중",
  RECEIVED: "반품입고",
  REFUNDED: "환불완료",
  REJECTED: "반품거절",
};

export const SHIPMENT_STATUS_LABELS: Record<string, string> = {
  PREPARING: "상품준비중",
  READY: "배송준비",
  PICKUP_REQUESTED: "수거요청",
  PICKED_UP: "집하완료",
  IN_TRANSIT: "배송중",
  OUT_FOR_DELIVERY: "배송출발",
  DELIVERED: "배송완료",
  RETURN_REQUESTED: "반품요청",
  RETURN_PICKUP_REQUESTED: "반품수거요청",
  RETURN_IN_TRANSIT: "반품배송중",
  RETURN_COMPLETED: "반품입고",
  CANCELLED: "배송취소",
};
