import { notFound } from "next/navigation";
import { AdminPage } from "@/components/admin/ops/AdminPage";
import { PurchaseDetailClient } from "@/components/admin/ops/PurchaseScreens";

export default async function AdminPurchaseDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!/^\d+$/.test(id)) notFound();
  return (
    <AdminPage next={`/admin/purchases/${id}`}>
      <PurchaseDetailClient ledgerId={Number(id)} />
    </AdminPage>
  );
}
