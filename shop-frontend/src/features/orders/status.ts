export const ORDER_STATUS_LABELS: Record<string, string> = {
  CREATED: "주문 접수",
  PAYMENT_PENDING: "결제 대기",
  PAID: "결제 완료",
  PREPARING: "상품 준비중",
  SHIPPED: "배송중",
  DELIVERED: "배송 완료",
  CANCEL_REQUESTED: "취소 요청중",
  CANCELLED: "주문 취소",
  RETURN_REQUESTED: "반품 진행중",
  RETURNED: "반품 완료",
};

export const CANCEL_REQUEST_STATUS_LABELS: Record<string, string> = {
  REQUESTED: "승인 대기",
  APPROVED: "취소 승인",
  REJECTED: "취소 거절",
};

export function orderStatusLabel(status: string): string {
  return ORDER_STATUS_LABELS[status] ?? status;
}

export function cancelRequestStatusLabel(status: string): string {
  return CANCEL_REQUEST_STATUS_LABELS[status] ?? status;
}

/** Unpaid orders are cancelled immediately by the member. */
export function canCancelImmediately(status: string): boolean {
  return status === "PAYMENT_PENDING";
}

/** Paid orders that have not shipped need admin approval to cancel. */
export function canRequestCancel(status: string): boolean {
  return status === "PAID" || status === "PREPARING";
}

export function orderStatusTone(status: string): "neutral" | "brand" | "warning" | "muted" {
  switch (status) {
    case "PAID":
    case "PREPARING":
    case "SHIPPED":
      return "brand";
    case "CANCEL_REQUESTED":
    case "PAYMENT_PENDING":
    case "RETURN_REQUESTED":
      return "warning";
    case "CANCELLED":
    case "RETURNED":
      return "muted";
    default:
      return "neutral";
  }
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "-";
  }
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "Asia/Seoul",
  }).format(date);
}
