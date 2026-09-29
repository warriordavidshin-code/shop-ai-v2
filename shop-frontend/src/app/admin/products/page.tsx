import { redirect } from "next/navigation";
import Link from "next/link";
import { Container } from "@/components/ui/Container";
import { AdminProductStockTable } from "@/components/admin/AdminProductStockTable";
import { AdminProductPager } from "@/components/admin/AdminProductPager";
import { loadAdminProductPage } from "@/features/admin/loadProducts";

export default async function AdminProductsPage({
  searchParams,
}: {
  searchParams: Promise<{ page?: string }>;
}) {
  const { page } = await searchParams;
  const data = await loadAdminProductPage(Number(page ?? 0));
  if (data === null) {
    redirect("/login?next=/admin/products");
  }

  return (
    <main className="py-10">
      <Container className="flex flex-col gap-6">
        <div className="flex items-center justify-between gap-3">
          <div className="flex flex-col gap-1">
            <h1 className="heading-ko text-2xl">상품 관리</h1>
            <p className="text-sm text-muted-foreground">
              재고 칸을 눌러 옵션별 재고 수량을 입력하고 저장할 수 있습니다. 총 {data.totalElements}개 상품
            </p>
          </div>
          <Link
            href="/admin/products/new"
            className="inline-flex h-11 items-center rounded-xl bg-brand px-4 text-sm font-medium text-white"
          >
            상품 등록
          </Link>
        </div>
        <AdminProductStockTable key={data.page} initialProducts={data.content} />
        <AdminProductPager basePath="/admin/products" page={data.page} totalPages={data.totalPages} />
      </Container>
    </main>
  );
}
