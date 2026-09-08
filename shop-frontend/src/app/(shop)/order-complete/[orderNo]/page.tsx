import Link from "next/link";
import { cookies } from "next/headers";
import { notFound, redirect } from "next/navigation";
import { Container } from "@/components/ui/Container";
import { formatKrw } from "@/lib/format";
import { orderSchema } from "@/features/orders/api";

type Params = Promise<{ orderNo: string }>;

async function fetchOrder(orderNo: string) {
  const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";
  const cookieStore = await cookies();
  const cookieHeader = cookieStore
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join("; ");
  const response = await fetch(`${backendUrl}/api/orders/${orderNo}`, {
    headers: cookieHeader ? { cookie: cookieHeader } : {},
    cache: "no-store",
  });
  if (response.status === 401) return "unauthorized" as const;
  if (!response.ok) return null;
  return orderSchema.parse(await response.json());
}

export default async function OrderCompletePage({ params }: { params: Params }) {
  const { orderNo } = await params;
  const order = await fetchOrder(orderNo);
  if (order === "unauthorized") redirect(`/login?next=/order-complete/${orderNo}`);
  if (!order) notFound();

  return (
    <main className="py-10">
      <Container className="flex max-w-2xl flex-col gap-6">
        <h1 className="text-2xl font-semibold">주문이 완료되었습니다</h1>
        <p className="text-sm text-muted-foreground">주문번호 {order.orderNo}</p>
        <p className="rounded-xl bg-brand-soft/50 px-3 py-2 text-sm">
          Mock 결제로 처리되었습니다. 실제 결제가 아닙니다.
        </p>
        <ul className="flex flex-col gap-2 text-sm">
          {order.items.map((item, idx) => (
            <li key={`${item.skuId}-${idx}`} className="flex justify-between border-b border-border py-2">
              <span>
                {item.productName} / {item.optionName} × {item.quantity}
              </span>
              <span className="tabular-nums">
                {formatKrw(item.paymentPrice ?? item.unitPrice * item.quantity)}원
              </span>
            </li>
          ))}
        </ul>
        <p className="text-base font-semibold tabular-nums">
          결제금액 {formatKrw(order.paymentAmount)}원
        </p>
        <div className="flex gap-3">
          <Link href={`/mypage/orders/${order.orderNo}`} className="text-brand hover:underline">
            주문 상세
          </Link>
          <Link href="/products" className="text-brand hover:underline">
            쇼핑 계속하기
          </Link>
        </div>
      </Container>
    </main>
  );
}
