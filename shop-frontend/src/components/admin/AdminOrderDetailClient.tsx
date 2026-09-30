"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { OrderStatusBadge } from "@/components/orders/OrderStatusBadge";
import { ApiError } from "@/lib/api/client";
import { formatKrw } from "@/lib/format";
import { cn } from "@/lib/utils";
import { formatDateTime } from "@/features/orders/status";
import { RETURN_STATUS_LABELS } from "@/features/shipping/progress";
import {
  canAdvanceShipment,
  changeShipmentStatus,
  getAdminOrder,
  isPickupRequested,
  issueWaybill,
  listDeliveryCompanies,
  MANUAL_SHIPMENT_TARGETS,
  printWaybill,
  refreshTracking,
  registerInvoice,
  requestDeliveryPickup,
  type AdminOrderDetail,
  type DeliveryCompany,
  type ShipmentView,
} from "@/features/admin/shipping";

const INVOICE_ORDER_STATUSES = new Set(["PAID", "PREPARING", "SHIPPED", "DELIVERED"]);

const inputClass = "h-10 rounded-lg border border-border bg-surface px-3 text-sm";
const smallButton =
  "h-9 rounded-lg border border-border px-3 text-xs hover:bg-surface-soft disabled:cursor-not-allowed disabled:opacity-50";

function message(e: unknown, fallback: string) {
  return e instanceof ApiError ? e.message : fallback;
}

export function AdminOrderDetailClient({ orderNo }: { orderNo: string }) {
  const [detail, setDetail] = useState<AdminOrderDetail | null>(null);
  const [companies, setCompanies] = useState<DeliveryCompany[]>([]);
  const [company, setCompany] = useState("");
  const [trackingNumber, setTrackingNumber] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const data = await getAdminOrder(orderNo);
      setDetail(data);
      setLoadError(null);
      if (data.delivery?.deliveryCompany) setCompany(data.delivery.deliveryCompany);
    } catch (e) {
      setLoadError(message(e, "주문을 불러오지 못했습니다."));
    }
  }, [orderNo]);

  useEffect(() => {
    void load();
    listDeliveryCompanies()
      .then(setCompanies)
      .catch(() => setCompanies([]));
  }, [load]);

  async function run(key: string, action: () => Promise<unknown>, done?: string): Promise<boolean> {
    setBusy(key);
    setError(null);
    setNotice(null);
    try {
      const res = await action();
      const msg = res && typeof res === "object" && "message" in res ? String((res as { message: string }).message) : done;
      setNotice(msg ?? "처리되었습니다.");
      await load();
      return true;
    } catch (e) {
      setError(message(e, "처리에 실패했습니다."));
      return false;
    } finally {
      setBusy(null);
    }
  }

  if (loadError) {
    return (
      <p className="text-sm text-danger" role="alert">
        {loadError}
      </p>
    );
  }
  if (!detail) return <p className="text-sm text-muted-foreground">불러오는 중...</p>;

  const { order, delivery, returnRequest, returnShipment } = detail;
  const orderId = order.orderId ?? 0;
  const enabledCompanies = companies.filter((c) => c.enabled);
  const selectedCompany = company || enabledCompanies[0]?.code || "";
  const canInvoice = INVOICE_ORDER_STATUSES.has(order.orderStatus);

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-xs text-muted-foreground">
            <Link href="/admin/orders" className="hover:underline">
              주문 관리
            </Link>{" "}
            / 주문 상세
          </p>
          <h1 className="heading-ko mt-1 text-2xl tabular-nums">{order.orderNo}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {detail.memberName ?? "-"} ({detail.memberLoginId ?? "-"}) · {formatDateTime(order.orderedAt)}
          </p>
        </div>
        <OrderStatusBadge status={order.orderStatus} className="text-sm" />
      </div>

      {notice ? (
        <p className="rounded-lg border border-brand/30 bg-brand-soft px-3 py-2 text-sm" role="status">
          {notice}
        </p>
      ) : null}
      {error ? (
        <p className="rounded-lg border border-danger/30 px-3 py-2 text-sm text-danger" role="alert">
          {error}
        </p>
      ) : null}

      <div className="grid gap-5 lg:grid-cols-[1fr_1fr]">
        <section className="rounded-xl border border-border bg-surface p-4 text-sm">
          <h2 className="mb-3 text-base font-semibold">주문 정보</h2>
          <ul className="flex flex-col gap-1">
            {order.items.map((item) => (
              <li key={`${item.skuId}-${item.optionName}`} className="flex justify-between gap-3">
                <span>
                  {item.productName} <span className="text-muted-foreground">/ {item.optionName}</span> × {item.quantity}
                </span>
                <span className="tabular-nums">{formatKrw(item.unitPrice * item.quantity)}원</span>
              </li>
            ))}
          </ul>
          <dl className="mt-3 grid grid-cols-[88px_1fr] gap-y-1 border-t border-border pt-3">
            <dt className="text-muted-foreground">배송비</dt>
            <dd className="tabular-nums">{order.deliveryAmount === 0 ? "무료" : `${formatKrw(order.deliveryAmount)}원`}</dd>
            <dt className="text-muted-foreground">결제금액</dt>
            <dd className="font-semibold tabular-nums">{formatKrw(order.paymentAmount)}원</dd>
            <dt className="text-muted-foreground">받는 분</dt>
            <dd>
              {order.receiverName} · {order.receiverPhone}
            </dd>
            <dt className="text-muted-foreground">주소</dt>
            <dd>
              ({order.postcode}) {order.address1} {order.address2 ?? ""}
            </dd>
            {order.orderMemo ? (
              <>
                <dt className="text-muted-foreground">요청사항</dt>
                <dd>{order.orderMemo}</dd>
              </>
            ) : null}
          </dl>
        </section>

        <section className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4 text-sm">
          <div className="flex items-center justify-between gap-2">
            <h2 className="text-base font-semibold">배송 관리</h2>
            <span className="rounded-full bg-brand-soft px-3 py-1 text-xs font-medium text-brand">
              {delivery?.statusName ?? "배송 정보 없음"}
            </span>
          </div>

          {delivery?.trackingNumber ? (
            <p>
              {delivery.deliveryCompanyName} · <span className="tabular-nums">{delivery.trackingNumber}</span>
              {delivery.trackingUrl ? (
                <a
                  href={delivery.trackingUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="ml-2 text-xs text-brand hover:underline"
                >
                  택배사 조회
                </a>
              ) : null}
            </p>
          ) : null}
          {delivery?.shippingProvider ? (
            <p className="text-xs text-muted-foreground">연동 업체: {delivery.shippingProvider}</p>
          ) : null}
          {delivery?.lastTrackingCheckedAt ? (
            <p className="text-xs text-muted-foreground">
              마지막 조회 {formatDateTime(delivery.lastTrackingCheckedAt)}
              {delivery.lastTrackingError ? ` · 조회 실패: ${delivery.lastTrackingError}` : ""}
            </p>
          ) : null}

          {canInvoice ? (
            <form
              className="flex flex-col gap-2 rounded-lg bg-surface-soft p-3"
              onSubmit={(e) => {
                e.preventDefault();
                void run("invoice", () => registerInvoice(orderId, selectedCompany, trackingNumber)).then((ok) => {
                  if (ok) setTrackingNumber("");
                });
              }}
            >
              <p className="font-medium">{delivery?.trackingNumber ? "송장번호 수정" : "송장번호 등록"}</p>
              <div className="flex flex-wrap gap-2">
                <select
                  className={inputClass}
                  value={selectedCompany}
                  onChange={(e) => setCompany(e.target.value)}
                  aria-label="택배사"
                >
                  {enabledCompanies.map((c) => (
                    <option key={c.code} value={c.code}>
                      {c.companyName}
                    </option>
                  ))}
                </select>
                <input
                  className={cn(inputClass, "min-w-0 flex-1")}
                  placeholder="송장번호 (숫자/영문)"
                  value={trackingNumber}
                  onChange={(e) => setTrackingNumber(e.target.value)}
                  inputMode="numeric"
                  required
                />
                <button
                  type="submit"
                  disabled={busy !== null || !selectedCompany}
                  className="h-10 rounded-lg bg-brand px-4 text-xs font-medium text-white hover:bg-brand-hover disabled:opacity-60"
                >
                  {busy === "invoice" ? "등록 중..." : delivery?.trackingNumber ? "송장 수정" : "송장 등록 · 배송 시작"}
                </button>
              </div>
              <p className="text-xs text-muted-foreground">
                송장을 등록하면 주문이 ‘배송중’으로 바뀌고 고객에게 발송 알림이 전송됩니다.
              </p>
            </form>
          ) : null}

          {delivery ? (
            <div className="flex flex-col gap-2">
              <p className="text-xs font-medium text-muted-foreground">배송 상태 수동 변경 (앞 단계로만 변경)</p>
              <div className="flex flex-wrap gap-1.5">
                {MANUAL_SHIPMENT_TARGETS.map((target) => (
                  <button
                    key={target.value}
                    type="button"
                    className={smallButton}
                    disabled={busy !== null || !canAdvanceShipment(delivery.status, target.value)}
                    onClick={() => {
                      if (!window.confirm(`배송 상태를 '${target.label}'(으)로 변경할까요?`)) return;
                      void run(`status-${target.value}`, () => changeShipmentStatus(orderId, target.value));
                    }}
                  >
                    {target.label}
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          <div className="flex flex-wrap gap-1.5 border-t border-border pt-3">
            <button
              type="button"
              className={smallButton}
              disabled={busy !== null || !canInvoice || isPickupRequested(delivery?.status)}
              onClick={() => void run("pickup", () => requestDeliveryPickup(orderId, selectedCompany))}
            >
              집하 요청
            </button>
            <button
              type="button"
              className={smallButton}
              disabled={busy !== null || !delivery?.trackingNumber}
              onClick={() => void run("refresh", () => refreshTracking(orderId, "DELIVERY"))}
            >
              {busy === "refresh" ? "조회 중..." : "배송조회 새로고침"}
            </button>
            <button
              type="button"
              className={smallButton}
              disabled={busy !== null || !canInvoice}
              onClick={() => void run("waybill", () => issueWaybill(orderId, selectedCompany), "운송장이 발급되었습니다.")}
            >
              운송장 발급
            </button>
            <button
              type="button"
              className={smallButton}
              disabled={busy !== null || !delivery?.trackingNumber}
              onClick={() =>
                void run("print", async () => {
                  const res = await printWaybill(orderId);
                  if (res.printUrl) window.open(res.printUrl, "_blank", "noopener,noreferrer");
                  return res;
                }, "운송장 출력을 요청했습니다.")
              }
            >
              운송장 출력
            </button>
          </div>
          <p className="text-xs text-muted-foreground">
            운송장 발급·출력과 집하 요청은 배송 설정에서 해당 기능이 켜진 외부 API 업체로 처리되며, 켜진 업체가 없으면 집하
            요청은 수동 기록으로 남습니다. 같은 버튼을 다시 눌러도 운송장이 두 번 발급되지 않습니다.
          </p>

          <EventTimeline shipment={delivery} />
        </section>
      </div>

      {returnRequest ? (
        <section className="rounded-xl border border-border bg-surface p-4 text-sm">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-base font-semibold">반품</h2>
            <Link href="/admin/returns?status=ALL" className="text-xs text-brand hover:underline">
              반품 관리에서 처리 →
            </Link>
          </div>
          <dl className="mt-3 grid grid-cols-[100px_1fr] gap-y-1">
            <dt className="text-muted-foreground">상태</dt>
            <dd className="font-medium">{RETURN_STATUS_LABELS[returnRequest.status] ?? returnRequest.statusName}</dd>
            <dt className="text-muted-foreground">사유</dt>
            <dd>
              {returnRequest.reasonLabel}
              {returnRequest.reasonText ? ` · ${returnRequest.reasonText}` : ""}
            </dd>
            {returnRequest.customerMemo ? (
              <>
                <dt className="text-muted-foreground">수거 요청사항</dt>
                <dd>{returnRequest.customerMemo}</dd>
              </>
            ) : null}
            <dt className="text-muted-foreground">수거지</dt>
            <dd>
              {returnRequest.pickupName} · {returnRequest.pickupPhone} · ({returnRequest.pickupPostcode}){" "}
              {returnRequest.pickupAddress1} {returnRequest.pickupAddress2 ?? ""}
            </dd>
            <dt className="text-muted-foreground">반품 배송비</dt>
            <dd>{returnRequest.freeReturn ? "무료 (판매자 부담)" : `${formatKrw(returnRequest.returnShippingFee)}원`}</dd>
            {returnRequest.pickupTrackingNumber ? (
              <>
                <dt className="text-muted-foreground">회수 송장</dt>
                <dd className="tabular-nums">
                  {returnRequest.pickupDeliveryCompanyName} {returnRequest.pickupTrackingNumber}
                  {returnRequest.shipmentStatusName ? ` · ${returnRequest.shipmentStatusName}` : ""}
                </dd>
              </>
            ) : null}
          </dl>
          <EventTimeline shipment={returnShipment} />
        </section>
      ) : null}
    </div>
  );
}

function EventTimeline({ shipment }: { shipment: ShipmentView | null | undefined }) {
  const events = [...(shipment?.events ?? [])].reverse();
  if (events.length === 0) return null;
  return (
    <div className="border-t border-border pt-3">
      <p className="mb-2 text-xs font-medium text-muted-foreground">처리 이력</p>
      <ol className="flex flex-col">
        {events.map((event, idx) => (
          <li key={`${event.timestamp ?? idx}-${idx}`} className="grid grid-cols-[110px_1fr] gap-3 py-1 text-xs">
            <span className="tabular-nums text-muted-foreground">{event.time ?? "-"}</span>
            <span>
              {event.description}
              {event.location ? <span className="text-muted-foreground"> · {event.location}</span> : null}
              {event.source === "PROVIDER" ? <span className="ml-1 text-muted-foreground">(택배사)</span> : null}
            </span>
          </li>
        ))}
      </ol>
    </div>
  );
}
