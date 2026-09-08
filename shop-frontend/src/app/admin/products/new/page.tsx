import { Container } from "@/components/ui/Container";
import { AdminProductForm } from "@/components/admin/AdminProductForm";

export default function AdminNewProductPage() {
  return (
    <main className="py-10">
      <Container>
        <h1 className="mb-6 text-2xl font-semibold">상품 등록</h1>
        <AdminProductForm mode="create" />
      </Container>
    </main>
  );
}
