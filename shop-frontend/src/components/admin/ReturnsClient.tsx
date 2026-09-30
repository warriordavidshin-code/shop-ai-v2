"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { ApiError } from "@/lib/api/client";
import { formatKrw } from "@/lib/format";
import { cn } from "@/lib/utils";
import { formatDateTime } from "@/features/orders/status";
import { RETURN_STATUS_LABELS } from "@/features/shipping/progress";
import {
  approveReturn,
  changeReturnStatus,
  listDeliveryCompanies,
  listReturns,
  refundReturn,
  registerReturnTracking,
  rejectReturn,
  requestReturnPickup,
  type AdminReturn,
  type DeliveryCompany,
  RETURN_FILTERS,
  type ReturnFilter,
} from "@/features/admin/shipping";

const STATUS_ORDER = ["REQUESTED", "APPROVED", "PICKUP_REQUESTED", "PICKED_UP", "IN_TRANSIT", "RECEIVED", "REFUNDED"];
const MANUAL_RETURN_TARGETS = [
  { value: "PICKED_UP", label: "기사 방문수거" },
  { value: "IN_TRANSIT", label: "반품배송중" },
  { value: "RECEIVED", label: "반품입고" },
] as const;

type Panel = { id: number; kind: "reject" | "pickup" | "tracking" | "refund" } | null;

const inputClass = "h-9 rounded-lg border border-border bg-surface px-2 text-sm";
const buttonClass = "h-9 rounded-lg border border-border px-3 text-xs hover:bg-surface-soft disabled:opacity-50";
const primaryClass = "h-9 rounded-lg bg-brand px-3 text-xs font-medium text-white hover:bg-brand-hover disabled:opacity-60";

export function ReturnsClient({ initialFilter }: { initialFilter: ReturnFilter }) {
  const router = useRouter();
  const [filter, setFilter] = useState<ReturnFilter>(initialFilter);
  const [rows, setRows] = useState<AdminReturn[] | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [companies, setCompanies] = useState<DeliveryCompany[]>([]);
  const [panel, setPanel] = useState<Panel>(null);
  const [reason, setReason] = useState("");
  const [company, setCompany] = useState("");
  const [trackingNumber, setTrackingNumber] = useState("");
  const [restock, setRestock] = useState(true);
  const [adminMemo, setAdminMemo] = useState("");
  const [busyId, setBusyId] = useState<number | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (nextFilter: ReturnFilter, nextPage: number) => {
    try {
      const data = await listReturns(nextFilter, nextPage);
      setRows(data.content);
      setPage(data.page);
      setTotalPages(data.totalPages);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "반품 목록을 불러오지 못했습니다.");
    }
  }, []);

  useEffect(() => {
    void load(filter, 0);
  }, [filter, load]);

  useEffect(() => {
    listDeliveryCompanies()
      .then(setCompanies)
      .catch(() => setCompanies([]));
  }, []);

  const enabledCompanies = companies.filter((c) => c.enabled);
  const selectedCompany = company || enabledCompanies[0]?.code || "";

  function openPanel(id: number, kind: NonNullable<Panel>["kind"]) {
    setError(null);
    setReason("");
    setTrackingNumber("");
    setRestock(true);
    setAdminMemo("");
    setPanel(panel?.id === id && panel.kind === kind ? null : { id, kind });
  }

  async function run(row: AdminReturn, action: () => Promise<unknown>, done: string) {
    setBusyId(row.returnRequest.returnRequestId);
    setNotice(null);
    setError(null);
    try {
      await action();
      setNotice(`주문 ${row.orderNo}: ${done}`);
      setPanel(null);
      await load(filter, page);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "처리에 실패했습니다.");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-1">
        {RETURN_FILTERS.map((f) => (
          <button
            key={f.value}
            type="button"
            onClick={() => {
              setRows(null);
              setFilter(f.value);
              setPanel(null);
              router.replace(`/admin/returns?status=${f.value}`, { scroll: false });
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
          해당하는 반품 요청이 없습니다.
        </p>
      ) : (
        <ul className="flex flex-col gap-3">
          {rows.map((row) => {
            const r = row.returnRequest;
            const id = r.returnRequestId;
            const busy = busyId !== null;
            const rank = STATUS_ORDER.indexOf(r.status);
            const expectedRefund = r.refundAmount ?? Math.max(row.paymentAmount - r.returnShippingFee, 0);
            return (
              <li key={id} className="rounded-xl border border-border bg-surface p-4 text-sm">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="flex flex-wrap items-center gap-2">
                      <Link
                        href={`/admin/orders/${encodeURIComponent(row.orderNo)}`}
                        className="font-medium text-brand hover:underline"
                      >
                        {row.orderNo}
                      </Link>
                      <span className="rounded-full border border-accent/40 bg-accent-soft px-2 py-0.5 text-xs font-medium">
                        {RETURN_STATUS_LABELS[r.status] ?? r.statusName}
                      </span>
                      <span className="text-xs text-muted-foreground">신청 {formatDateTime(r.requestedAt)}</span>
                    </p>
                    <p className="mt-1">
                      {row.itemSummary ?? "-"} · <span className="tabular-nums">{formatKrw(row.paymentAmount)}원</span>
                    </p>
                    <p className="mt-1 text-muted-foreground">
                      {row.memberName ?? "-"} ({row.memberLoginId ?? `#${row.memberId ?? "-"}`})
                    </p>
                    <p className="mt-2 rounded-lg bg-surface-soft px-3 py-2">
                      사유: {r.reasonLabel}
                      {r.memo ? ` · ${r.memo}` : ""}
                    </p>
                    <p className="mt-1 text-muted-foreground">
                      수거지: {r.pickupName} · {r.pickupPhone} · ({r.pickupPostcode}) {r.pickupAddress1}{" "}
                      {r.pickupAddress2 ?? ""}
                    </p>
                    <p className="mt-1 text-muted-foreground">
                      반품 배송비 {r.freeReturn ? "무료(판매자 부담)" : `${formatKrw(r.returnShippingFee)}원`} ·{" "}
                      {r.status === "REFUNDED" ? "환불" : "예상 환불"}{" "}
                      <span className="font-medium text-foreground tabular-nums">{formatKrw(expectedRefund)}원</span>
                      {r.status === "REFUNDED" && r.restocked ? " · 재입고 완료" : ""}
                    </p>
                    {r.pickupTrackingNumber ? (
                      <p className="mt-1 text-muted-foreground">
                        회수 송장: {r.pickupDeliveryCompanyName} <span className="tabular-nums">{r.pickupTrackingNumber}</span>
                        {r.pickupTrackingUrl ? (
                          <a
                            href={r.pickupTrackingUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="ml-2 text-xs text-brand hover:underline"
                          >
                            조회
                          </a>
                        ) : null}
                      </p>
                    ) : null}
                    {r.rejectReason ? <p className="mt-1 text-danger">거절 사유: {r.rejectReason}</p> : null}
                  </div>

                  <div className="flex flex-wrap justify-end gap-1.5">
                    {r.status === "REQUESTED" ? (
                      <button
                        type="button"
                        className={primaryClass}
                        disabled={busy}
                        onClick={() => {
                          if (!window.confirm(`주문 ${row.orderNo}의 반품을 승인할까요?`)) return;
                          void run(row, () => approveReturn(id), "반품을 승인했습니다.");
                        }}
                      >
                        승인
                      </button>
                    ) : null}
                    {r.status === "APPROVED" ? (
                      <button type="button" className={primaryClass} disabled={busy} onClick={() => openPanel(id, "pickup")}>
                        반품수거 요청
                      </button>
                    ) : null}
                    {r.status === "PICKUP_REQUESTED" || r.status === "PICKED_UP" || r.status === "IN_TRANSIT" ? (
                      <button type="button" className={buttonClass} disabled={busy} onClick={() => openPanel(id, "tracking")}>
                        회수 송장 {r.pickupTrackingNumber ? "수정" : "등록"}
                      </button>
                    ) : null}
                    {rank >= STATUS_ORDER.indexOf("PICKUP_REQUESTED") && rank < STATUS_ORDER.indexOf("RECEIVED")
                      ? MANUAL_RETURN_TARGETS.filter((t) => STATUS_ORDER.indexOf(t.value) > rank).map((target) => (
                          <button
                            key={target.value}
                            type="button"
                            className={buttonClass}
                            disabled={busy}
                            onClick={() => {
                              if (!window.confirm(`반품 상태를 '${target.label}'(으)로 변경할까요?`)) return;
                              void run(row, () => changeReturnStatus(id, target.value), `${target.label}(으)로 변경했습니다.`);
                            }}
                          >
                            {target.label}
                          </button>
                        ))
                      : null}
                    {r.status === "RECEIVED" ? (
                      <button type="button" className={primaryClass} disabled={busy} onClick={() => openPanel(id, "refund")}>
                        환불 처리
                      </button>
                    ) : null}
                    {r.status === "REQUESTED" || r.status === "APPROVED" || r.status === "PICKUP_REQUESTED" ? (
                      <button type="button" className={buttonClass} disabled={busy} onClick={() => openPanel(id, "reject")}>
                        거절
                      </button>
                    ) : null}
                  </div>
                </div>

                {panel?.id === id ? (
                  <div className="mt-3 flex flex-col gap-2 border-t border-border pt-3 sm:flex-row sm:flex-wrap sm:items-center">
                    {panel.kind === "reject" ? (
                      <>
                        <input
                          className={cn(inputClass, "flex-1")}
                          placeholder="거절 사유 (고객에게 표시됩니다)"
                          maxLength={500}
                          value={reason}
                          onChange={(e) => setReason(e.target.value)}
                        />
                        <button
                          type="button"
                          disabled={busy || !reason.trim()}
                          className="h-9 rounded-lg bg-danger px-4 text-xs font-medium text-white disabled:opacity-60"
                          onClick={() => void run(row, () => rejectReturn(id, reason.trim()), "반품 요청을 거절했습니다.")}
                        >
                          거절 확정
                        </button>
                      </>
                    ) : null}

                    {panel.kind === "pickup" || panel.kind === "tracking" ? (
                      <>
                        <select
                          className={inputClass}
                          value={selectedCompany}
                          onChange={(e) => setCompany(e.target.value)}
                          aria-label="회수 택배사"
                        >
                          {enabledCompanies.map((c) => (
                            <option key={c.code} value={c.code}>
                              {c.companyName}
                            </option>
                          ))}
                        </select>
                        <input
                          className={cn(inputClass, "flex-1")}
                          placeholder={panel.kind === "pickup" ? "회수 송장번호 (나중에 등록 가능)" : "회수 송장번호"}
                          value={trackingNumber}
                          onChange={(e) => setTrackingNumber(e.target.value)}
                        />
                        <button
                          type="button"
                          disabled={busy || (panel.kind === "tracking" && !trackingNumber.trim())}
                          className={primaryClass}
                          onClick={() =>
                            void run(
                              row,
                              () =>
                                panel.kind === "pickup"
                                  ? requestReturnPickup(id, selectedCompany, trackingNumber.trim())
                                  : registerReturnTracking(id, selectedCompany, trackingNumber.trim()),
                              panel.kind === "pickup" ? "반품 수거를 요청했습니다." : "회수 송장을 등록했습니다.",
                            )
                          }
                        >
                          {panel.kind === "pickup" ? "수거 요청 기록" : "송장 저장"}
                        </button>
                        {panel.kind === "pickup" ? (
                          <p className="w-full text-xs text-muted-foreground">
                            택배사 반품 회수 API 연동 전에는 택배사에 직접 회수를 접수한 뒤 이 화면에 기록합니다.
                          </p>
                        ) : null}
                      </>
                    ) : null}

                    {panel.kind === "refund" ? (
                      <>
                        <label className="flex items-center gap-2">
                          <input type="checkbox" checked={restock} onChange={(e) => setRestock(e.target.checked)} />
                          검수 통과 · 재고 복원
                        </label>
                        <input
                          className={cn(inputClass, "flex-1")}
                          placeholder="관리자 메모 (선택)"
                          maxLength={500}
                          value={adminMemo}
                          onChange={(e) => setAdminMemo(e.target.value)}
                        />
                        <button
                          type="button"
                          disabled={busy}
                          className={primaryClass}
                          onClick={() => {
                            if (!window.confirm(`${formatKrw(expectedRefund)}원을 환불 처리할까요?`)) return;
                            void run(row, () => refundReturn(id, restock, adminMemo.trim()), "환불 처리를 완료했습니다.");
                          }}
                        >
                          {formatKrw(expectedRefund)}원 환불
                        </button>
                      </>
                    ) : null}
                  </div>
                ) : null}
              </li>
            );
          })}
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
