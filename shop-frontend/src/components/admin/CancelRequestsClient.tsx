"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError } from "@/lib/api/client";
import { formatKrw } from "@/lib/format";
import {
  approveCancelRequest,
  listCancelRequests,
  rejectCancelRequest,
  type AdminCancelRequest,
  type CancelRequestFilter,
} from "@/features/admin/cancelRequests";
import { cancelRequestStatusLabel, formatDateTime, orderStatusLabel } from "@/features/orders/status";
import { cn } from "@/lib/utils";

const FILTERS: { value: CancelRequestFilter; label: string }[] = [
  { value: "REQUESTED", label: "승인 대기" },
  { value: "APPROVED", label: "승인 완료" },
  { value: "REJECTED", label: "거절" },
  { value: "ALL", label: "전체" },
];

const statusClass: Record<AdminCancelRequest["status"], string> = {
  REQUESTED: "border-accent/40 bg-accent-soft text-foreground",
  APPROVED: "border-brand/30 bg-brand-soft text-brand",
  REJECTED: "border-border bg-surface-soft text-muted-foreground",
};

export function CancelRequestsClient() {
  const [filter, setFilter] = useState<CancelRequestFilter>("REQUESTED");
  const [rows, setRows] = useState<AdminCancelRequest[] | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [rejectingId, setRejectingId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async (nextFilter: CancelRequestFilter, nextPage: number) => {
    try {
      const data = await listCancelRequests(nextFilter, nextPage);
      setRows(data.content);
      setPage(data.page);
      setTotalPages(data.totalPages);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "취소 요청 목록을 불러오지 못했습니다.");
    }
  }, []);

  useEffect(() => {
    void load(filter, 0);
  }, [filter, load]);

  async function onApprove(row: AdminCancelRequest) {
    if (!window.confirm(`주문 ${row.orderNo}의 취소를 승인할까요?\n재고가 복원되고 결제가 취소 처리됩니다.`)) return;
    setBusyId(row.cancelRequestId);
    setNotice(null);
    setError(null);
    try {
      await approveCancelRequest(row.cancelRequestId);
      setNotice(`주문 ${row.orderNo} 취소를 승인했습니다.`);
      await load(filter, page);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "승인 처리에 실패했습니다.");
    } finally {
      setBusyId(null);
    }
  }

  async function onReject(row: AdminCancelRequest) {
    const reason = rejectReason.trim();
    if (!reason) {
      setError("거절 사유를 입력해 주세요.");
      return;
    }
    setBusyId(row.cancelRequestId);
    setNotice(null);
    setError(null);
    try {
      await rejectCancelRequest(row.cancelRequestId, reason);
      setNotice(`주문 ${row.orderNo} 취소 요청을 거절했습니다.`);
      setRejectingId(null);
      setRejectReason("");
      await load(filter, page);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "거절 처리에 실패했습니다.");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-1">
        {FILTERS.map((f) => (
          <button
            key={f.value}
            type="button"
            onClick={() => {
              setRows(null);
              setFilter(f.value);
            }}
            className={cn(
              "rounded-md px-3 py-2 text-sm transition-colors",
              filter === f.value
                ? "bg-brand text-white"
                : "border border-border text-muted-foreground hover:bg-surface-soft hover:text-foreground",
            )}
          >
            {f.label}
          </button>
        ))}
      </div>

      {notice ? (
        <p className="rounded-lg border border-brand/30 bg-brand-soft px-3 py-2 text-sm" role="status">
          {notice}
        </p>
      ) : null}
      {error ? (
        <p className="text-sm text-danger" role="alert">
          {error}
        </p>
      ) : null}

      {rows === null ? (
        <p className="text-sm text-muted-foreground">불러오는 중...</p>
      ) : rows.length === 0 ? (
        <p className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          해당하는 취소 요청이 없습니다.
        </p>
      ) : (
        <ul className="flex flex-col gap-3">
          {rows.map((row) => (
            <li key={row.cancelRequestId} className="rounded-xl border border-border bg-surface p-4 text-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="flex flex-wrap items-center gap-2">
                    <span className="font-medium text-foreground">{row.orderNo}</span>
                    <span
                      className={cn(
                        "rounded-full border px-2 py-0.5 text-xs font-medium",
                        statusClass[row.status],
                      )}
                    >
                      {cancelRequestStatusLabel(row.status)}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      주문 상태: {orderStatusLabel(row.orderStatus)}
                      {row.status === "REQUESTED" ? ` (요청 전: ${orderStatusLabel(row.previousOrderStatus)})` : ""}
                    </span>
                  </p>
                  <p className="mt-1 text-foreground">
                    {row.itemSummary ?? "-"} · <span className="tabular-nums">{formatKrw(row.paymentAmount)}원</span>
                  </p>
                  <p className="mt-1 text-muted-foreground">
                    {row.memberName ?? "-"} ({row.memberLoginId ?? `#${row.memberId}`}) · 요청{" "}
                    {formatDateTime(row.requestedAt)}
                    {row.processedAt ? ` · 처리 ${formatDateTime(row.processedAt)}` : ""}
                  </p>
                  <p className="mt-2 rounded-lg bg-surface-soft px-3 py-2">사유: {row.reason}</p>
                  {row.rejectReason ? <p className="mt-1 text-danger">거절 사유: {row.rejectReason}</p> : null}
                </div>
                {row.status === "REQUESTED" ? (
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => void onApprove(row)}
                      disabled={busyId !== null}
                      className="h-9 rounded-lg bg-brand px-4 text-xs font-medium text-white hover:bg-brand-hover disabled:opacity-60"
                    >
                      {busyId === row.cancelRequestId && rejectingId !== row.cancelRequestId ? "처리 중..." : "승인"}
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setError(null);
                        setRejectReason("");
                        setRejectingId(rejectingId === row.cancelRequestId ? null : row.cancelRequestId);
                      }}
                      disabled={busyId !== null}
                      className="h-9 rounded-lg border border-border px-4 text-xs hover:bg-surface-soft disabled:opacity-60"
                    >
                      거절
                    </button>
                  </div>
                ) : null}
              </div>
              {rejectingId === row.cancelRequestId ? (
                <div className="mt-3 flex flex-col gap-2 border-t border-border pt-3 sm:flex-row">
                  <input
                    className="h-10 flex-1 rounded-lg border border-border bg-surface px-3"
                    placeholder="거절 사유 (고객에게 표시됩니다)"
                    maxLength={500}
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    disabled={busyId !== null}
                  />
                  <button
                    type="button"
                    onClick={() => void onReject(row)}
                    disabled={busyId !== null}
                    className="h-10 rounded-lg bg-danger px-4 text-xs font-medium text-white disabled:opacity-60"
                  >
                    {busyId === row.cancelRequestId ? "처리 중..." : "거절 확정"}
                  </button>
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      )}

      {totalPages > 1 ? (
        <div className="flex items-center justify-center gap-3 text-sm">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => void load(filter, page - 1)}
            className="rounded-md border border-border px-3 py-1.5 disabled:opacity-40"
          >
            이전
          </button>
          <span className="tabular-nums text-muted-foreground">
            {page + 1} / {totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= totalPages}
            onClick={() => void load(filter, page + 1)}
            className="rounded-md border border-border px-3 py-1.5 disabled:opacity-40"
          >
            다음
          </button>
        </div>
      ) : null}
    </div>
  );
}
