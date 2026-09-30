import Link from "next/link";
import { Container } from "@/components/ui/Container";
import { AdminOrdersClient } from "@/components/admin/AdminOrdersClient";
import { requireAdmin } from "@/features/admin/guard";
import { toOrderView } from "@/features/admin/shipping";

type SearchParams = Promise<{ view?: string; keyword?: string }>;

export default async function AdminOrdersPage({ searchParams }: { searchParams: SearchParams }) {
  await requireAdmin("/admin/orders?page=0&size=1", "/admin/orders");
  const { view, keyword } = await searchParams;

  return (
    <main className="py-10">
      <Container className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="heading-ko text-2xl">주문 · 배송 관리</h1>
          <div className="flex gap-4 text-sm">
            <Link href="/admin/returns" className="text-brand hover:underline">
              반품 관리 →
            </Link>
            <Link href="/admin/cancel-requests" className="text-brand hover:underline">
              취소 요청 관리 →
            </Link>
          </div>
        </div>
        <AdminOrdersClient initialView={toOrderView(view)} initialKeyword={keyword ?? ""} />
      </Container>
    </main>
  );
}
