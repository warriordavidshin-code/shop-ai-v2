"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { BulkInvoicePanel } from "@/components/admin/BulkInvoicePanel";
import { OrderStatusBadge } from "@/components/orders/OrderStatusBadge";
import { TrackingModal } from "@/components/orders/ShipmentTracker";
import { ApiError } from "@/lib/api/client";
import { formatKrw } from "@/lib/format";
import { cn } from "@/lib/utils";
import { formatDateTime } from "@/features/orders/status";
import type { Tracking } from "@/features/shipping/api";
import {
  getAdminOrder,
  listAdminOrders,
  listDeliveryCompanies,
  ORDER_VIEWS,
  shipmentToTracking,
  type AdminOrderRow,
  type DeliveryCompany,
  type OrderView,
} from "@/features/admin/shipping";

/** Orders that can still receive an invoice (결제완료 / 상품준비중 / 배송중). */
const INVOICE_TARGETS = new Set(["PAID", "PREPARING", "SHIPPED"]);

type Props = {
  initialView: OrderView;
  initialKeyword: string;
};

export function AdminOrdersClient({ initialView, initialKeyword }: Props) {
  const router = useRouter();
  const [view, setView] = useState<OrderView>(initialView);
  const [keyword, setKeyword] = useState(initialKeyword);
  const [query, setQuery] = useState(initialKeyword);
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<AdminOrderRow[] | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [companies, setCompanies] = useState<DeliveryCompany[]>([]);
  const [selected, setSelected] = useState<Record<string, AdminOrderRow>>({});
  const [trackingOpen, setTrackingOpen] = useState(false);
  const [tracking, setTracking] = useState<Tracking | null>(null);
  const [trackingLoading, setTrackingLoading] = useState(false);
  const [trackingError, setTrackingError] = useState<string | null>(null);

  const load = useCallback(async (nextView: OrderView, nextQuery: string, nextPage: number) => {
    try {
      const data = await listAdminOrders({ view: nextView, keyword: nextQuery, page: nextPage, size: 20 });
      setRows(data.content);
      setPage(data.page);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
      setError(null);
    } catch (e) {
      if (e instanceof ApiError && (e.status === 401 || e.status === 403)) {
        router.replace("/login?next=/admin/orders");
        return;
      }
      setError(e instanceof ApiError ? e.message : "주문 목록을 불러오지 못했습니다.");
    }
  }, [router]);

  useEffect(() => {
    void load(view, query, 0);
  }, [view, query, load]);

  useEffect(() => {
    listDeliveryCompanies()
      .then(setCompanies)
      .catch(() => setCompanies([]));
  }, []);

  function changeView(next: OrderView) {
    setRows(null);
    setView(next);
    setSelected({});
    const params = new URLSearchParams();
    if (next !== "ALL") params.set("view", next);
    router.replace(`/admin/orders${params.size ? `?${params.toString()}` : ""}`, { scroll: false });
  }

  function toggle(row: AdminOrderRow) {
    setSelected((prev) => {
      const next = { ...prev };
      if (next[row.orderNo]) delete next[row.orderNo];
      else next[row.orderNo] = row;
      return next;
    });
  }

  const selectable = (rows ?? []).filter((row) => INVOICE_TARGETS.has(row.orderStatus));
  const allChecked = selectable.length > 0 && selectable.every((row) => selected[row.orderNo]);

  function toggleAll() {
    setSelected((prev) => {
      const next = { ...prev };
      for (const row of selectable) {
        if (allChecked) delete next[row.orderNo];
        else next[row.orderNo] = row;
      }
      return next;
    });
  }

  async function openTracking(row: AdminOrderRow) {
    setTrackingOpen(true);
    setTracking(null);
    setTrackingError(null);
    setTrackingLoading(true);
    try {
      const detail = await getAdminOrder(row.orderNo);
      setTracking(
        shipmentToTracking(detail.delivery, {
          orderId: row.orderId,
          orderNo: row.orderNo,
          orderStatus: row.orderStatus,
        }),
      );
    } catch (e) {
      setTrackingError(e instanceof ApiError ? e.message : "배송정보를 불러오지 못했습니다.");
    } finally {
      setTrackingLoading(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-1">
          {ORDER_VIEWS.map((v) => (
            <button
              key={v.value}
              type="button"
              onClick={() => changeView(v.value)}
              className={cn(
                "rounded-md px-3 py-2 text-sm transition-colors",
                view === v.value
                  ? "bg-brand text-white"
                  : "border border-border text-muted-foreground hover:bg-surface-soft hover:text-foreground",
              )}
            >
              {v.label}
            </button>
          ))}
        </div>
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            setRows(null);
            setQuery(keyword.trim());
          }}
        >
          <input
            className="h-10 w-52 rounded-lg border border-border bg-surface px-3 text-sm"
            placeholder="주문번호 검색"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          <button type="submit" className="h-10 rounded-lg border border-border px-4 text-sm hover:bg-surface-soft">
            검색
          </button>
        </form>
      </div>

      <BulkInvoicePanel
        companies={companies}
        selected={Object.values(selected)}
        onClearSelection={() => setSelected({})}
        onDone={() => void load(view, query, page)}
      />

      {error ? (
        <p className="text-sm text-danger" role="alert">
          {error}
        </p>
      ) : null}

      <p className="text-xs text-muted-foreground">총 {totalElements}건</p>
      <div className="overflow-x-auto rounded-xl border border-border">
        <table className="min-w-[1100px] text-left text-sm">
          <thead className="bg-surface-soft text-muted-foreground">
            <tr>
              <th className="px-3 py-3">
                <input
                  type="checkbox"
                  aria-label="송장 등록 가능한 주문 전체 선택"
                  checked={allChecked}
                  onChange={toggleAll}
                  disabled={selectable.length === 0}
                />
              </th>
              <th className="px-3 py-3">주문번호</th>
              <th className="px-3 py-3">고객명</th>
              <th className="px-3 py-3">상품명</th>
              <th className="px-3 py-3 text-right">주문금액</th>
              <th className="px-3 py-3">배송상태</th>
              <th className="px-3 py-3">택배사</th>
              <th className="px-3 py-3">송장번호</th>
              <th className="px-3 py-3">수거요청</th>
              <th className="px-3 py-3">배송조회</th>
              <th className="px-3 py-3">반품상태</th>
            </tr>
          </thead>
          <tbody>
            {rows === null ? (
              <tr>
                <td colSpan={11} className="px-4 py-8 text-center text-muted-foreground">
                  불러오는 중...
                </td>
              </tr>
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={11} className="px-4 py-8 text-center text-muted-foreground">
                  주문이 없습니다.
                </td>
              </tr>
            ) : (
              rows.map((row) => (
                <tr key={row.orderNo} className="border-t border-border align-top">
                  <td className="px-3 py-3">
                    <input
                      type="checkbox"
                      aria-label={`${row.orderNo} 선택`}
                      checked={!!selected[row.orderNo]}
                      onChange={() => toggle(row)}
                      disabled={!INVOICE_TARGETS.has(row.orderStatus)}
                    />
                  </td>
                  <td className="px-3 py-3">
                    <Link href={`/admin/orders/${encodeURIComponent(row.orderNo)}`} className="text-brand hover:underline">
                      {row.orderNo}
                    </Link>
                    <p className="text-xs text-muted-foreground">{formatDateTime(row.orderedAt)}</p>
                  </td>
                  <td className="px-3 py-3">
                    {row.memberName ?? row.receiverName ?? "-"}
                    {row.memberLoginId ? <p className="text-xs text-muted-foreground">{row.memberLoginId}</p> : null}
                  </td>
                  <td className="max-w-[220px] px-3 py-3">
                    <p className="truncate" title={row.itemSummary ?? undefined}>
                      {row.itemSummary ?? "-"}
                    </p>
                  </td>
                  <td className="px-3 py-3 text-right tabular-nums">{formatKrw(row.paymentAmount)}원</td>
                  <td className="px-3 py-3">
                    <div className="flex flex-col items-start gap-1">
                      <OrderStatusBadge status={row.orderStatus} />
                      {row.shipmentStatusName ? (
                        <span className="text-xs text-muted-foreground">{row.shipmentStatusName}</span>
                      ) : null}
                    </div>
                  </td>
                  <td className="px-3 py-3">{row.deliveryCompanyName ?? "-"}</td>
                  <td className="px-3 py-3 tabular-nums">{row.trackingNumber ?? "-"}</td>
                  <td className="px-3 py-3">{row.pickupStatus ?? "-"}</td>
                  <td className="px-3 py-3">
                    {row.trackingNumber ? (
                      <button
                        type="button"
                        onClick={() => void openTracking(row)}
                        className="rounded-md border border-border px-2 py-1 text-xs hover:bg-surface-soft"
                      >
                        조회
                      </button>
                    ) : (
                      <span className="text-muted-foreground">-</span>
                    )}
                  </td>
                  <td className="px-3 py-3">
                    {row.returnStatusName ? (
                      <Link href="/admin/returns?status=ALL" className="text-xs text-accent hover:underline">
                        {row.returnStatusName}
                      </Link>
                    ) : (
                      "-"
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {totalPages > 1 ? (
        <div className="flex items-center justify-center gap-3 text-sm">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => void load(view, query, page - 1)}
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
            onClick={() => void load(view, query, page + 1)}
            className="rounded-md border border-border px-3 py-1.5 disabled:opacity-40"
          >
            다음
          </button>
        </div>
      ) : null}

      {trackingOpen ? (
        <TrackingModal
          tracking={tracking}
          loading={trackingLoading}
          error={trackingError}
          onClose={() => setTrackingOpen(false)}
        />
      ) : null}
    </div>
  );
}
