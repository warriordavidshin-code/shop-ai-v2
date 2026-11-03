import { AdminPage } from "@/components/admin/ops/AdminPage";
import { MatchingClient } from "@/components/admin/ops/MatchingClient";

export default async function AdminPurchaseMatchingPage({
  searchParams,
}: {
  searchParams: Promise<{ ledgerId?: string }>;
}) {
  const { ledgerId } = await searchParams;
  const id = ledgerId && /^\d+$/.test(ledgerId) ? Number(ledgerId) : undefined;
  return (
    <AdminPage next="/admin/purchases/matching">
      <MatchingClient ledgerId={id} />
    </AdminPage>
  );
}
