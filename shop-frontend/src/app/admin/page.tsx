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

  const cards = [
    { label: "판매중 상품", value: data.onSaleProductCount ?? "-" },
    { label: "재고 부족", value: data.lowStockCount ?? "-" },
    { label: "최근 주문(7일)", value: data.recentOrderCount ?? "-" },
    { label: "결제 대기", value: data.pendingPaymentCount ?? "-" },
    { label: "회원 수", value: data.memberCount ?? "-" },
  ];

  return (
    <main className="py-10">
      <Container className="flex flex-col gap-8">
        <h1 className="text-2xl font-semibold">관리자 대시보드</h1>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {cards.map((card) => (
            <div key={card.label} className="rounded-xl border border-border bg-surface p-5">
              <p className="text-sm text-muted-foreground">{card.label}</p>
              <p className="mt-2 text-2xl font-semibold tabular-nums">{card.value}</p>
            </div>
          ))}
        </div>
      </Container>
    </main>
  );
}
