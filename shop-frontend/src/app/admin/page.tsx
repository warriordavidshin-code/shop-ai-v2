import Link from "next/link";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { Container } from "@/components/ui/Container";

async function fetchDashboard() {
  const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";
  const cookieStore = await cookies();
  const cookieHeader = cookieStore
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join("; ");
  const response = await fetch(`${backendUrl}/api/admin/dashboard`, {
    headers: cookieHeader ? { cookie: cookieHeader } : {},
    cache: "no-store",
  });
  if (response.status === 401 || response.status === 403) return null;
  if (!response.ok) return {};
  return response.json();
}

export default async function AdminDashboardPage() {
  const data = await fetchDashboard();
  if (data === null) redirect("/login?next=/admin");

  const shippingCards = [
    { label: "오늘 주문", value: data.todayOrderCount ?? "-", href: "/admin/orders?view=TODAY" },
    { label: "배송 준비", value: data.shippingReadyCount ?? "-", href: "/admin/orders?view=READY" },
    { label: "배송중", value: data.shippingInTransitCount ?? "-", href: "/admin/orders?view=SHIPPING" },
    { label: "배송완료", value: data.deliveredCount ?? "-", href: "/admin/orders?view=DELIVERED" },
    { label: "반품 요청", value: data.returnRequestCount ?? "-", href: "/admin/returns?status=OPEN" },
  ];

  const cards = [
    { label: "판매중 상품", value: data.onSaleProductCount ?? "-" },
    { label: "재고 부족", value: data.lowStockCount ?? "-" },
    { label: "최근 주문(7일)", value: data.recentOrderCount ?? "-" },
    { label: "결제 대기", value: data.pendingPaymentCount ?? "-" },
    { label: "취소 요청 대기", value: data.cancelRequestCount ?? "-", href: "/admin/cancel-requests" },
    { label: "회원 수", value: data.memberCount ?? "-" },
  ];

  return (
    <main className="py-10">
      <Container className="flex flex-col gap-8">
        <h1 className="heading-ko text-2xl">관리자 대시보드</h1>
        <section className="flex flex-col gap-3">
          <h2 className="text-base font-semibold">주문 · 배송</h2>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
            {shippingCards.map((card) => (
              <Link
                key={card.label}
                href={card.href}
                className="rounded-xl border border-border bg-surface p-5 transition-colors hover:border-brand/40 hover:bg-surface-soft"
              >
                <p className="text-sm text-muted-foreground">{card.label}</p>
                <p className="mt-2 text-2xl font-semibold tabular-nums text-foreground">{card.value}</p>
              </Link>
            ))}
          </div>
        </section>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {cards.map((card) => {
            const content = (
              <>
                <p className="text-sm text-muted-foreground">{card.label}</p>
                <p className="mt-2 text-2xl font-semibold tabular-nums text-foreground">{card.value}</p>
              </>
            );
            return card.href ? (
              <Link
                key={card.label}
                href={card.href}
                className="rounded-xl border border-border bg-surface p-5 transition-colors hover:bg-surface-soft"
              >
                {content}
              </Link>
            ) : (
              <div key={card.label} className="rounded-xl border border-border bg-surface p-5">
                {content}
              </div>
            );
          })}
        </div>
      </Container>
    </main>
  );
}
