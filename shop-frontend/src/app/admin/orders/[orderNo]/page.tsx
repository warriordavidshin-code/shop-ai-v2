import { notFound } from "next/navigation";
import { Container } from "@/components/ui/Container";
import { AdminOrderDetailClient } from "@/components/admin/AdminOrderDetailClient";
import { requireAdmin } from "@/features/admin/guard";

type Params = Promise<{ orderNo: string }>;

export default async function AdminOrderDetailPage({ params }: { params: Params }) {
  const { orderNo } = await params;
  const probe = await requireAdmin(`/admin/orders/${encodeURIComponent(orderNo)}`, `/admin/orders/${orderNo}`);
  if (probe.status === 404) notFound();

  return (
    <main className="py-10">
      <Container>
        <AdminOrderDetailClient orderNo={orderNo} />
      </Container>
    </main>
  );
}
