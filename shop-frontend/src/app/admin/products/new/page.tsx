import { Container } from "@/components/ui/Container";
import { AdminProductForm } from "@/components/admin/AdminProductForm";

export default async function AdminNewProductPage({
  searchParams,
}: {
  searchParams: Promise<{ ledgerItemId?: string }>;
}) {
  const { ledgerItemId } = await searchParams;
  const itemId = ledgerItemId && /^\d+$/.test(ledgerItemId) ? Number(ledgerItemId) : undefined;
  return (
    <main className="py-10">
      <Container>
        <h1 className="mb-6 heading-ko text-2xl">{itemId ? "매입 품목으로 신규 상품 등록" : "상품 등록"}</h1>
        <AdminProductForm mode="create" ledgerItemId={itemId} />
      </Container>
    </main>
  );
}
