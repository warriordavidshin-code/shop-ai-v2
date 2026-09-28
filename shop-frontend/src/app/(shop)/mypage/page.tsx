import Link from "next/link";
import { redirect } from "next/navigation";
import { z } from "zod";
import { MypageClient } from "@/components/member/MypageClient";
import { OrderStatusBadge } from "@/components/orders/OrderStatusBadge";
import { memberSchema } from "@/features/auth/schemas";
import { orderSummarySchema } from "@/features/orders/api";
import { formatDateTime } from "@/features/orders/status";
import { backendFetch } from "@/lib/api/server";
import { formatKrw } from "@/lib/format";

async function fetchMe() {
  const response = await backendFetch("/members/me");
  if (!response.ok) return null;
  return memberSchema.parse(await response.json());
}

async function fetchRecentOrders() {
  const response = await backendFetch("/members/me/orders?page=0&size=5");
  if (!response.ok) return [];
  const data = z.object({ content: z.array(orderSummarySchema) }).safeParse(await response.json());
  return data.success ? data.data.content : [];
}

type SearchParams = Promise<{ updated?: string }>;

export default async function MypagePage({ searchParams }: { searchParams: SearchParams }) {
  const { updated } = await searchParams;
  const member = await fetchMe();
  if (!member) {
    redirect("/login?next=/mypage");
  }
  const orders = await fetchRecentOrders();

  return (
    <div className="flex flex-col gap-8">
      <h1 className="heading-ko text-2xl text-foreground md:hidden">마이페이지</h1>
      {updated ? (
        <p className="rounded-lg border border-brand/30 bg-brand-soft px-4 py-3 text-sm text-foreground" role="status">
          개인정보가 수정되었습니다.
        </p>
      ) : null}
      <MypageClient member={member} />

      <section className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">최근 주문</h2>
          <Link href="/mypage/orders" className="text-sm text-brand hover:underline">
            전체 보기
          </Link>
        </div>
        {orders.length === 0 ? (
          <p className="rounded-xl border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
            아직 주문내역이 없습니다.
          </p>
        ) : (
          <ul className="divide-y divide-border rounded-xl border border-border bg-surface">
            {orders.map((order) => (
              <li key={order.orderNo}>
                <Link
                  href={`/mypage/orders/${order.orderNo}`}
                  className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 text-sm hover:bg-surface-soft"
                >
                  <div className="min-w-0">
                    <p className="truncate font-medium text-foreground">{order.itemSummary ?? order.orderNo}</p>
                    <p className="text-xs text-muted-foreground">
                      {formatDateTime(order.orderedAt)} · {order.orderNo}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="tabular-nums">{formatKrw(order.paymentAmount)}원</span>
                    <OrderStatusBadge status={order.orderStatus} />
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="grid gap-3 sm:grid-cols-2">
        <Link
          href="/mypage/addresses"
          className="rounded-xl border border-border bg-surface p-5 hover:bg-surface-soft"
        >
          <p className="font-medium text-foreground">배송지 관리</p>
          <p className="mt-1 text-sm text-muted-foreground">자주 쓰는 배송지를 저장하고 기본 배송지를 지정하세요.</p>
        </Link>
        <Link href="/mypage/verify" className="rounded-xl border border-border bg-surface p-5 hover:bg-surface-soft">
          <p className="font-medium text-foreground">개인정보 수정</p>
          <p className="mt-1 text-sm text-muted-foreground">본인 인증 후 이름, 연락처, 비밀번호를 변경할 수 있습니다.</p>
        </Link>
      </section>
    </div>
  );
}
