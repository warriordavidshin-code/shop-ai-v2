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

export default async function OrderDetailPage({ params }: { params: Params }) {
  const { orderNo } = await params;
  const order = await fetchOrder(orderNo);
  if (order === "unauthorized") redirect(`/login?next=/mypage/orders/${orderNo}`);
  if (!order) notFound();

  return (
    <main className="py-8">
      <Container className="flex max-w-2xl flex-col gap-4">
        <Link href="/mypage/orders" className="text-sm text-brand hover:underline">
          ← 주문내역
        </Link>
        <h1 className="text-2xl font-semibold">주문 상세</h1>
        <p className="text-sm text-muted-foreground">
          {order.orderNo} · {order.orderStatus}
        </p>
        <ul className="text-sm">
          {order.items.map((item, idx) => (
            <li key={`${item.skuId}-${idx}`} className="flex justify-between border-b border-border py-2">
              <span>
                {item.productName} / {item.optionName} × {item.quantity}
              </span>
              <span className="tabular-nums">{formatKrw(item.unitPrice)}원</span>
            </li>
          ))}
        </ul>
        <p className="font-semibold tabular-nums">합계 {formatKrw(order.paymentAmount)}원</p>
        <div className="text-sm text-muted-foreground">
          <p>
            {order.receiverName} · {order.receiverPhone}
          </p>
          <p>
            ({order.postcode}) {order.address1} {order.address2}
          </p>
        </div>
      </Container>
    </main>
  );
}
