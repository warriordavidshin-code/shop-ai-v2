"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { formatKrw } from "@/lib/format";
import { ApiError } from "@/lib/api/client";
import { listMyOrders } from "@/features/orders/api";

export function OrdersClient() {
  const [rows, setRows] = useState<
    Array<{ orderNo: string; orderStatus: string; paymentAmount: number; orderedAt?: string }>
  >([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      try {
        const page = await listMyOrders();
        setRows(
          page.content.map((o) => ({
            orderNo: o.orderNo,
            orderStatus: o.orderStatus,
            paymentAmount: o.paymentAmount,
            orderedAt: o.orderedAt,
          })),
        );
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "주문내역을 불러오지 못했습니다.");
      }
    })();
  }, []);

  if (error) return <p className="text-sm text-danger">{error}</p>;
  if (rows.length === 0) {
    return <p className="text-sm text-muted-foreground">주문내역이 없습니다.</p>;
  }

  return (
    <ul className="flex flex-col gap-3">
      {rows.map((row) => (
        <li key={row.orderNo} className="rounded-xl border border-border bg-surface p-4 text-sm">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <Link href={`/mypage/orders/${row.orderNo}`} className="font-medium text-brand hover:underline">
              {row.orderNo}
            </Link>
            <span>{row.orderStatus}</span>
          </div>
          <p className="mt-2 tabular-nums">{formatKrw(row.paymentAmount)}원</p>
        </li>
      ))}
    </ul>
  );
}
