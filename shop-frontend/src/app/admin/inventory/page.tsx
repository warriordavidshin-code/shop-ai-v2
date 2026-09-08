import { Container } from "@/components/ui/Container";
import { InventoryAdjustForm } from "@/components/admin/InventoryAdjustForm";

export default function AdminInventoryPage() {
  return (
    <main className="py-10">
      <Container className="flex flex-col gap-6">
        <h1 className="text-2xl font-semibold">재고 관리</h1>
        <InventoryAdjustForm />
      </Container>
    </main>
  );
}
