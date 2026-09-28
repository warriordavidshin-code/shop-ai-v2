"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { ApiError } from "@/lib/api/client";
import { cancelOrder, requestOrderCancel, type CancelRequestInfo } from "@/features/orders/api";
import {
  canCancelImmediately,
  canRequestCancel,
  cancelRequestStatusLabel,
  formatDateTime,
} from "@/features/orders/status";

const REASONS = ["단순 변심", "주문 실수(옵션/수량 변경)", "배송 지연", "다른 상품으로 재주문", "기타"];

type Props = {
  orderNo: string;
  orderStatus: string;
  cancelRequest?: CancelRequestInfo | null;
};

export function OrderCancelPanel({ orderNo, orderStatus, cancelRequest }: Props) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState(REASONS[0]);
  const [detail, setDetail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onCancelNow() {
    if (!window.confirm("결제 전 주문을 취소할까요?")) return;
    setBusy(true);
    setError(null);
    try {
      await cancelOrder(orderNo);
      router.refresh();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "주문 취소에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  async function onSubmitRequest(e: React.FormEvent) {
    e.preventDefault();
    const text = reason === "기타" ? detail.trim() : detail.trim() ? `${reason} - ${detail.trim()}` : reason;
    if (!text) {
      setError("취소 사유를 입력해 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await requestOrderCancel(orderNo, text.slice(0, 500));
      setOpen(false);
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "취소 요청에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  const history =
    cancelRequest && cancelRequest.status !== "REQUESTED" ? (
      <div className="rounded-lg bg-surface-soft p-3 text-sm">
        <p className="font-medium">
          최근 취소 요청: {cancelRequestStatusLabel(cancelRequest.status)}
          <span className="ml-2 text-xs font-normal text-muted-foreground">
            {formatDateTime(cancelRequest.processedAt ?? cancelRequest.requestedAt)}
          </span>
        </p>
        <p className="mt-1 text-muted-foreground">요청 사유: {cancelRequest.reason}</p>
        {cancelRequest.status === "REJECTED" && cancelRequest.rejectReason ? (
          <p className="mt-1 text-danger">거절 사유: {cancelRequest.rejectReason}</p>
        ) : null}
      </div>
    ) : null;

  let body: React.ReactNode;
  if (orderStatus === "CANCEL_REQUESTED") {
    body = (
      <div className="rounded-lg border border-accent/40 bg-accent-soft p-3 text-sm">
        <p className="font-medium">취소 요청이 접수되어 관리자 승인을 기다리고 있습니다.</p>
        {cancelRequest ? (
          <p className="mt-1 text-muted-foreground">
            요청일 {formatDateTime(cancelRequest.requestedAt)} · 사유: {cancelRequest.reason}
          </p>
        ) : null}
      </div>
    );
  } else if (orderStatus === "CANCELLED") {
    body = <p className="text-sm text-muted-foreground">취소된 주문입니다. 결제 금액은 결제 수단으로 환불됩니다.</p>;
  } else if (canCancelImmediately(orderStatus)) {
    body = (
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">결제 전 주문은 바로 취소할 수 있습니다.</p>
        <Button variant="secondary" onClick={onCancelNow} disabled={busy}>
          {busy ? "취소 중..." : "주문 취소"}
        </Button>
      </div>
    );
  } else if (canRequestCancel(orderStatus)) {
    body = open ? (
      <form onSubmit={onSubmitRequest} className="flex flex-col gap-3">
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">취소 사유</span>
          <select
            className="h-11 rounded-xl border border-border bg-surface px-3"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            disabled={busy}
          >
            {REASONS.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">상세 사유 {reason === "기타" ? "(필수)" : "(선택)"}</span>
          <textarea
            className="min-h-24 rounded-xl border border-border bg-surface px-3 py-2"
            value={detail}
            maxLength={400}
            onChange={(e) => setDetail(e.target.value)}
            disabled={busy}
          />
        </label>
        <div className="flex gap-2">
          <Button type="submit" disabled={busy}>
            {busy ? "요청 중..." : "취소 요청하기"}
          </Button>
          <Button variant="secondary" onClick={() => setOpen(false)} disabled={busy}>
            닫기
          </Button>
        </div>
      </form>
    ) : (
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">
          결제가 완료된 주문은 취소 요청 후 관리자 승인 시 취소·환불됩니다.
        </p>
        <Button variant="secondary" onClick={() => setOpen(true)}>
          취소 요청
        </Button>
      </div>
    );
  } else {
    body = (
      <p className="text-sm text-muted-foreground">
        배송이 시작된 주문은 취소할 수 없습니다. 반품·교환은 고객센터로 문의해 주세요.
      </p>
    );
  }

  return (
    <section className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4">
      <h2 className="text-base font-semibold">주문 취소</h2>
      {body}
      {history}
      {error ? (
        <p className="text-sm text-danger" role="alert">
          {error}
        </p>
      ) : null}
    </section>
  );
}
