import { redirect } from "next/navigation";
import { Container } from "@/components/ui/Container";
import { AdminProductStockTable } from "@/components/admin/AdminProductStockTable";
import { AdminProductPager } from "@/components/admin/AdminProductPager";
import { loadAdminProductPage } from "@/features/admin/loadProducts";

export default async function AdminInventoryPage({
  searchParams,
}: {
  searchParams: Promise<{ page?: string }>;
}) {
  const { page } = await searchParams;
  const data = await loadAdminProductPage(Number(page ?? 0));
  if (data === null) {
    redirect("/login?next=/admin/inventory");
  }

  return (
    <main className="py-10">
      <Container className="flex flex-col gap-6">
        <div className="flex flex-col gap-1">
          <h1 className="heading-ko text-2xl">재고 관리</h1>
          <p className="text-sm text-muted-foreground">
            상품별 옵션 재고를 수정합니다. 변경 내역은 재고 이력과 감사 로그에 기록됩니다.
          </p>
        </div>
        <AdminProductStockTable key={data.page} initialProducts={data.content} defaultExpanded />
        <AdminProductPager basePath="/admin/inventory" page={data.page} totalPages={data.totalPages} />
      </Container>
    </main>
  );
}
