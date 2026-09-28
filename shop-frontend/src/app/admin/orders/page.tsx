import Link from "next/link";
import { redirect } from "next/navigation";
import { Container } from "@/components/ui/Container";
import { OrderStatusBadge } from "@/components/orders/OrderStatusBadge";
import { formatDateTime } from "@/features/orders/status";
import { backendFetch } from "@/lib/api/server";
import { formatKrw } from "@/lib/format";

type AdminOrderRow = {
  orderNo: string;
  orderStatus: string;
  paymentAmount: number;
  orderedAt?: string | null;
  itemCount?: number;
};

async function fetchAdminOrders() {
  const response = await backendFetch("/admin/orders?page=0&size=50");
  if (response.status === 401 || response.status === 403) return null;
  if (!response.ok) return { content: [] };
  return response.json();
}

export default async function AdminOrdersPage() {
  const data = await fetchAdminOrders();
  if (data === null) redirect("/login?next=/admin/orders");
  const rows: AdminOrderRow[] = Array.isArray(data.content) ? data.content : [];

  return (
    <main className="py-10">
      <Container className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="heading-ko text-2xl">주문 관리</h1>
          <Link href="/admin/cancel-requests" className="text-sm text-brand hover:underline">
            취소 요청 관리 →
          </Link>
        </div>
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-surface-soft text-muted-foreground">
              <tr>
                <th className="px-4 py-3">주문번호</th>
                <th className="px-4 py-3">주문일시</th>
                <th className="px-4 py-3">상태</th>
                <th className="px-4 py-3">상품 수</th>
                <th className="px-4 py-3 text-right">금액</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.orderNo} className="border-t border-border">
                  <td className="px-4 py-3">{row.orderNo}</td>
                  <td className="px-4 py-3 text-muted-foreground">{formatDateTime(row.orderedAt)}</td>
                  <td className="px-4 py-3">
                    {row.orderStatus === "CANCEL_REQUESTED" ? (
                      <Link href="/admin/cancel-requests">
                        <OrderStatusBadge status={row.orderStatus} />
                      </Link>
                    ) : (
                      <OrderStatusBadge status={row.orderStatus} />
                    )}
                  </td>
                  <td className="px-4 py-3 tabular-nums">{row.itemCount ?? "-"}</td>
                  <td className="px-4 py-3 text-right tabular-nums">{formatKrw(row.paymentAmount)}원</td>
                </tr>
              ))}
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-muted-foreground">
                    주문이 없습니다.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </Container>
    </main>
  );
}
