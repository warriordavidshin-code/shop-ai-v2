import { AdminPage } from "@/components/admin/ops/AdminPage";
import { TransactionsClient } from "@/components/admin/ops/InventoryScreens";

export default async function AdminInventoryTransactionsPage({
  searchParams,
}: {
  searchParams: Promise<{ skuId?: string }>;
}) {
  const { skuId } = await searchParams;
  return (
    <AdminPage next="/admin/inventory/transactions">
      <TransactionsClient skuId={skuId && /^\d+$/.test(skuId) ? Number(skuId) : undefined} />
    </AdminPage>
  );
}
