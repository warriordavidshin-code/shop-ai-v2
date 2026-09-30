"use client";

import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils";
import { ApiError } from "@/lib/api/client";
import { getTracking, type Tracking } from "@/features/shipping/api";
import { DELIVERY_STEPS, deliveryStepIndex } from "@/features/shipping/progress";

const TEMPORARY_FAILURE = "배송정보를 일시적으로 조회할 수 없습니다. 잠시 후 다시 확인해주세요.";

type Props = {
  orderId: number;
  orderStatus: string;
};

/** Delivery progress bar plus the 배송조회 modal on the order detail page. */
export function ShipmentTracker({ orderId, orderStatus }: Props) {
  const [tracking, setTracking] = useState<Tracking | null>(null);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setTracking(await getTracking(orderId));
    } catch (e) {
      setError(e instanceof ApiError && e.status < 500 ? e.message : TEMPORARY_FAILURE);
    } finally {
      setLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    void load();
  }, [load]);

  const current = deliveryStepIndex(orderStatus, tracking?.status);
  if (current < 0) {
    return null;
  }
  const hasInvoice = !!tracking?.trackingNumber;

  return (
    <section className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-base font-semibold">배송 현황</h2>
        <Button
          variant="secondary"
          className="h-9 px-4"
          onClick={() => {
            setOpen(true);
            void load();
          }}
        >
          배송조회
        </Button>
      </div>

      <ol className="grid grid-cols-5 gap-1" aria-label="배송 진행 단계">
        {DELIVERY_STEPS.map((step, index) => {
          const done = index < current;
          const active = index === current;
          return (
            <li key={step.key} className="flex flex-col items-center gap-1.5 text-center">
              <span
                className={cn(
                  "flex h-8 w-8 items-center justify-center rounded-full border text-xs font-semibold",
                  active && "border-brand bg-brand text-white",
                  done && "border-brand bg-brand-soft text-brand",
                  !active && !done && "border-border bg-surface-soft text-muted-foreground",
                )}
                aria-current={active ? "step" : undefined}
              >
                {index + 1}
              </span>
              <span className={cn("text-xs", active ? "font-semibold text-brand" : "text-muted-foreground")}>
                {step.label}
              </span>
            </li>
          );
        })}
      </ol>

      {hasInvoice ? (
        <p className="text-sm text-muted-foreground">
          {tracking?.deliveryCompanyName} · 송장번호 <span className="tabular-nums">{tracking?.trackingNumber}</span>
        </p>
      ) : (
        <p className="text-sm text-muted-foreground">송장번호가 등록되면 배송조회를 할 수 있습니다.</p>
      )}
      {tracking?.message || error ? (
        <p className="text-sm text-accent" role="status">
          {tracking?.message ?? error}
        </p>
      ) : null}

      {open ? (
        <TrackingModal tracking={tracking} loading={loading} error={error} onClose={() => setOpen(false)} />
      ) : null}
    </section>
  );
}

export function TrackingModal({
  tracking,
  loading,
  error,
  title = "배송조회",
  onClose,
}: {
  tracking: Tracking | null;
  loading: boolean;
  error: string | null;
  title?: string;
  onClose: () => void;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const events = [...(tracking?.events ?? [])].reverse();

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-0 sm:items-center sm:p-4"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={onClose}
    >
      <div
        className="max-h-[85vh] w-full max-w-lg overflow-y-auto rounded-t-2xl bg-surface p-5 shadow-xl sm:rounded-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-semibold">{title}</h3>
          <button type="button" className="text-sm text-muted-foreground hover:text-foreground" onClick={onClose}>
            닫기
          </button>
        </div>

        <dl className="mt-4 grid grid-cols-[88px_1fr] gap-y-2 text-sm">
          <dt className="text-muted-foreground">택배사</dt>
          <dd>{tracking?.deliveryCompanyName ?? "-"}</dd>
          <dt className="text-muted-foreground">송장번호</dt>
          <dd className="flex flex-wrap items-center gap-2 tabular-nums">
            {tracking?.trackingNumber ?? "-"}
            {tracking?.trackingUrl ? (
              <a
                href={tracking.trackingUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="text-xs text-brand hover:underline"
              >
                택배사 사이트에서 보기
              </a>
            ) : null}
          </dd>
          <dt className="text-muted-foreground">현재 상태</dt>
          <dd className="font-semibold text-brand">{tracking?.statusName ?? "상품 준비 전"}</dd>
        </dl>

        {tracking?.message || error ? (
          <p className="mt-3 rounded-lg bg-accent-soft px-3 py-2 text-sm" role="status">
            {tracking?.message ?? error}
          </p>
        ) : null}

        <h4 className="mt-5 text-sm font-semibold">배송 이력</h4>
        {loading && !tracking ? (
          <p className="mt-2 text-sm text-muted-foreground">불러오는 중...</p>
        ) : events.length === 0 ? (
          <p className="mt-2 text-sm text-muted-foreground">아직 배송 이력이 없습니다.</p>
        ) : (
          <ol className="mt-2 flex flex-col">
            {events.map((event, idx) => (
              <li
                key={`${event.timestamp ?? idx}-${idx}`}
                className="grid grid-cols-[110px_1fr] gap-3 border-b border-border py-2 text-sm last:border-b-0"
              >
                <span className="tabular-nums text-muted-foreground">{event.time ?? "-"}</span>
                <span>
                  <span className={cn(idx === 0 && "font-semibold text-foreground")}>{event.description}</span>
                  {event.location ? <span className="ml-1 text-muted-foreground">· {event.location}</span> : null}
                </span>
              </li>
            ))}
          </ol>
        )}
      </div>
    </div>
  );
}
