"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { DaumPostcodeFields } from "@/components/address/DaumPostcodeFields";
import { TrackingModal } from "@/components/orders/ShipmentTracker";
import { cn } from "@/lib/utils";
import { formatKrw } from "@/lib/format";
import { ApiError } from "@/lib/api/client";
import { formatDateTime } from "@/features/orders/status";
import { getReturnInfo, getTracking, requestReturn, type ReturnInfo, type Tracking } from "@/features/shipping/api";
import { RETURN_STEPS, returnStepIndex } from "@/features/shipping/progress";

type Props = {
  orderId: number;
  orderStatus: string;
  paymentAmount: number;
  receiverName: string;
  receiverPhone: string;
  postcode: string;
  address1: string;
  address2?: string | null;
};

/** 반품 신청 (only for delivered orders) and return progress on the order detail page. */
export function ReturnPanel(props: Props) {
  const { orderId, orderStatus } = props;
  const router = useRouter();
  const [info, setInfo] = useState<ReturnInfo | null>(null);
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [memo, setMemo] = useState("");
  const [pickupName, setPickupName] = useState(props.receiverName);
  const [pickupPhone, setPickupPhone] = useState(props.receiverPhone);
  const [postcode, setPostcode] = useState(props.postcode);
  const [address1, setAddress1] = useState(props.address1);
  const [address2, setAddress2] = useState(props.address2 ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tracking, setTracking] = useState<Tracking | null>(null);
  const [trackingOpen, setTrackingOpen] = useState(false);
  const [trackingError, setTrackingError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setInfo(await getReturnInfo(orderId));
    } catch {
      setInfo(null);
    }
  }, [orderId]);

  useEffect(() => {
    if (orderStatus === "DELIVERED" || orderStatus === "RETURN_REQUESTED" || orderStatus === "RETURNED") {
      void load();
    }
  }, [load, orderStatus]);

  if (!info) return null;
  const current = info.returnRequest;
  const selectedReason = info.reasons.find((r) => r.code === reason);
  const fee = selectedReason?.freeReturn ? 0 : info.returnShippingFee;

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!reason) {
      setError("반품 사유를 선택해 주세요.");
      return;
    }
    if (!pickupName.trim() || !pickupPhone.trim() || !postcode || !address1) {
      setError("수거지 정보를 모두 입력해 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await requestReturn(orderId, {
        returnReason: reason,
        returnMemo: memo.trim() || undefined,
        pickupName: pickupName.trim(),
        pickupPhone: pickupPhone.trim(),
        pickupPostcode: postcode,
        pickupAddress: address1,
        pickupAddressDetail: address2.trim() || undefined,
      });
      setOpen(false);
      await load();
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "반품 신청에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  async function openReturnTracking() {
    setTrackingOpen(true);
    setTrackingError(null);
    try {
      setTracking(await getTracking(orderId, "RETURN"));
    } catch (e) {
      setTrackingError(e instanceof ApiError ? e.message : "반품 배송정보를 불러오지 못했습니다.");
    }
  }

  const notice = (
    <ul className="list-disc space-y-1 pl-5 text-xs text-muted-foreground">
      <li>반품 상품이 입고되면 상품 확인 후 환불이 진행됩니다.</li>
      <li>상품 불량·오배송은 무료 반품이며, 그 외 사유는 반품 배송비 {formatKrw(info.returnShippingFee)}원이 환불 금액에서 차감됩니다.</li>
      <li>착용·세탁 흔적이 있거나 택이 제거된 상품은 반품이 거절될 수 있습니다.</li>
    </ul>
  );

  let body: React.ReactNode = null;
  if (current && current.status !== "REJECTED") {
    const step = returnStepIndex(current.status);
    body = (
      <div className="flex flex-col gap-3">
        <ol className="flex flex-wrap gap-1.5" aria-label="반품 진행 단계">
          {RETURN_STEPS.map((s, index) => (
            <li
              key={s.key}
              aria-current={index === step ? "step" : undefined}
              className={cn(
                "rounded-full px-2.5 py-1 text-xs",
                index === step && "bg-brand font-semibold text-white",
                index < step && "bg-brand-soft text-brand",
                index > step && "bg-surface-soft text-muted-foreground",
              )}
            >
              {s.label}
            </li>
          ))}
        </ol>
        <dl className="grid grid-cols-[88px_1fr] gap-y-1 text-sm">
          <dt className="text-muted-foreground">반품 사유</dt>
          <dd>
            {current.reasonLabel}
            {current.memo ? <span className="text-muted-foreground"> · {current.memo}</span> : null}
          </dd>
          <dt className="text-muted-foreground">신청일</dt>
          <dd>{formatDateTime(current.requestedAt)}</dd>
          <dt className="text-muted-foreground">수거지</dt>
          <dd className="break-words">
            ({current.pickupPostcode}) {current.pickupAddress1} {current.pickupAddress2}
          </dd>
          <dt className="text-muted-foreground">환불 예정</dt>
          <dd className="tabular-nums">
            {formatKrw(current.refundAmount ?? 0)}원
            {current.freeReturn ? (
              <span className="ml-1 text-xs text-brand">(무료 반품)</span>
            ) : (
              <span className="ml-1 text-xs text-muted-foreground">
                (반품 배송비 {formatKrw(current.returnShippingFee)}원 차감)
              </span>
            )}
          </dd>
        </dl>
        {current.pickupTrackingNumber ? (
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="text-muted-foreground">
              반품 송장 {current.pickupDeliveryCompanyName} {current.pickupTrackingNumber}
            </span>
            <Button variant="secondary" className="h-9 px-4" onClick={() => void openReturnTracking()}>
              반품 배송조회
            </Button>
          </div>
        ) : null}
        {current.status === "REFUNDED" ? (
          <p className="text-sm text-brand">환불이 완료되었습니다. 결제 수단으로 환불됩니다.</p>
        ) : (
          notice
        )}
      </div>
    );
  } else if (info.canRequest) {
    body = open ? (
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">반품 사유</span>
          <select
            className="h-11 rounded-xl border border-border bg-surface px-3"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            disabled={busy}
            required
          >
            <option value="">선택해 주세요</option>
            {info.reasons.map((r) => (
              <option key={r.code} value={r.code}>
                {r.label}
                {r.freeReturn ? " (무료 반품)" : ""}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">상세 내용 (선택)</span>
          <textarea
            className="min-h-20 rounded-xl border border-border bg-surface px-3 py-2"
            value={memo}
            maxLength={1000}
            onChange={(e) => setMemo(e.target.value)}
            disabled={busy}
          />
        </label>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium">수거 담당자</span>
            <input
              className="h-11 rounded-xl border border-border px-3"
              value={pickupName}
              maxLength={100}
              onChange={(e) => setPickupName(e.target.value)}
              disabled={busy}
              required
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium">수거 연락처</span>
            <input
              className="h-11 rounded-xl border border-border px-3"
              value={pickupPhone}
              maxLength={20}
              onChange={(e) => setPickupPhone(e.target.value)}
              disabled={busy}
              required
            />
          </label>
        </div>
        <DaumPostcodeFields
          postcode={postcode}
          address1={address1}
          address2={address2}
          onPostcodeChange={setPostcode}
          onAddress1Change={setAddress1}
          onAddress2Change={setAddress2}
          disabled={busy}
        />
        <p className="rounded-lg bg-surface-soft px-3 py-2 text-sm">
          예상 환불 금액{" "}
          <strong className="tabular-nums">{formatKrw(Math.max(props.paymentAmount - fee, 0))}원</strong>
          {selectedReason?.freeReturn ? (
            <span className="ml-1 text-xs text-brand">(무료 반품)</span>
          ) : (
            <span className="ml-1 text-xs text-muted-foreground">(반품 배송비 {formatKrw(fee)}원 차감)</span>
          )}
        </p>
        {notice}
        <div className="flex gap-2">
          <Button type="submit" disabled={busy}>
            {busy ? "신청 중..." : "반품 신청하기"}
          </Button>
          <Button variant="secondary" onClick={() => setOpen(false)} disabled={busy}>
            닫기
          </Button>
        </div>
      </form>
    ) : (
      <div className="flex flex-col gap-3">
        {current?.status === "REJECTED" ? (
          <p className="rounded-lg bg-surface-soft p-3 text-sm">
            이전 반품 신청이 거절되었습니다.
            {current.rejectReason ? <span className="text-danger"> 사유: {current.rejectReason}</span> : null}
          </p>
        ) : null}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-muted-foreground">배송이 완료된 상품은 반품을 신청할 수 있습니다.</p>
          <Button variant="secondary" onClick={() => setOpen(true)}>
            반품 신청
          </Button>
        </div>
      </div>
    );
  }

  if (!body) return null;
  return (
    <section className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4">
      <h2 className="text-base font-semibold">반품</h2>
      {body}
      {error ? (
        <p className="text-sm text-danger" role="alert">
          {error}
        </p>
      ) : null}
      {trackingOpen ? (
        <TrackingModal
          title="반품 배송조회"
          tracking={tracking}
          loading={!tracking && !trackingError}
          error={trackingError}
          onClose={() => setTrackingOpen(false)}
        />
      ) : null}
    </section>
  );
}
