import { Container } from "@/components/ui/Container";
import { ReturnsClient } from "@/components/admin/ReturnsClient";
import { requireAdmin } from "@/features/admin/guard";
import { toReturnFilter } from "@/features/admin/shipping";

type SearchParams = Promise<{ status?: string }>;

export default async function AdminReturnsPage({ searchParams }: { searchParams: SearchParams }) {
  await requireAdmin("/admin/returns?status=OPEN&page=0&size=1", "/admin/returns");
  const { status } = await searchParams;

  return (
    <main className="py-10">
      <Container className="flex flex-col gap-4">
        <div>
          <h1 className="heading-ko text-2xl">반품 관리</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            반품신청 → 승인 → 반품수거 요청 → 기사 방문수거 → 반품배송중 → 반품입고(검수) → 환불 순서로 처리합니다. 상품
            불량·오배송은 반품 배송비가 무료입니다.
          </p>
        </div>
        <ReturnsClient initialFilter={toReturnFilter(status)} />
      </Container>
    </main>
  );
}
