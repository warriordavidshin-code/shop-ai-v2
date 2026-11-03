import { AdminPage } from "@/components/admin/ops/AdminPage";
import { ReceiptsClient } from "@/components/admin/ops/ReceiptsClient";

function toId(value?: string): number | undefined {
  return value && /^\d+$/.test(value) ? Number(value) : undefined;
}

export default async function AdminReceiptsPage({
  searchParams,
}: {
  searchParams: Promise<{ ledgerId?: string; receiptId?: string }>;
}) {
  const { ledgerId, receiptId } = await searchParams;
  return (
    <AdminPage next="/admin/inventory/receipts">
      <ReceiptsClient ledgerId={toId(ledgerId)} receiptId={toId(receiptId)} />
    </AdminPage>
  );
}
