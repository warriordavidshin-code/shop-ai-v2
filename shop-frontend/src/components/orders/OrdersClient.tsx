"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { formatKrw } from "@/lib/format";
import { ApiError } from "@/lib/api/client";
import { listMyOrders, type OrderSummary } from "@/features/orders/api";
import { formatDateTime } from "@/features/orders/status";
import { OrderStatusBadge } from "@/components/orders/OrderStatusBadge";

const PAGE_SIZE = 10;

export function OrdersClient() {
  const router = useRouter();
  const [rows, setRows] = useState<OrderSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (nextPage: number) => {
      setLoading(true);
      try {
        const data = await listMyOrders(nextPage, PAGE_SIZE);
        setRows((prev) => (nextPage === 0 ? data.content : [...prev, ...data.content]));
        setPage(data.page);
        setTotalPages(data.totalPages);
        setError(null);
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) {
          router.push("/login?next=/mypage/orders");
          return;
        }
        setError(e instanceof ApiError ? e.message : "주문내역을 불러오지 못했습니다.");
      } finally {
        setLoading(false);
      }
    },
    [router],
  );

  useEffect(() => {
    void load(0);
  }, [load]);

  if (error) return <p className="text-sm text-danger">{error}</p>;
  if (loading && rows.length === 0) {
    return <p className="text-sm text-muted-foreground">불러오는 중...</p>;
  }
  if (rows.length === 0) {
    return (
      <p className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
        주문내역이 없습니다.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <ul className="flex flex-col gap-3">
        {rows.map((row) => (
          <li key={row.orderNo}>
            <Link
              href={`/mypage/orders/${row.orderNo}`}
              className="block rounded-xl border border-border bg-surface p-4 text-sm transition-colors hover:bg-surface-soft"
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-xs text-muted-foreground">
                  {formatDateTime(row.orderedAt)} · {row.orderNo}
                </span>
                <OrderStatusBadge status={row.orderStatus} />
              </div>
              <p className="mt-2 font-medium text-foreground">{row.itemSummary ?? "주문 상품"}</p>
              <p className="mt-1 tabular-nums">{formatKrw(row.paymentAmount)}원</p>
            </Link>
          </li>
        ))}
      </ul>
      {page + 1 < totalPages ? (
        <button
          type="button"
          onClick={() => void load(page + 1)}
          disabled={loading}
          className="mx-auto inline-flex h-10 items-center rounded-xl border border-border bg-surface px-5 text-sm hover:bg-surface-soft disabled:opacity-60"
        >
          {loading ? "불러오는 중..." : "더 보기"}
        </button>
      ) : null}
    </div>
  );
}
