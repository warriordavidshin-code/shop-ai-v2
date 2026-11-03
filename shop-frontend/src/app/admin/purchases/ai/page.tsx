import { AdminPage } from "@/components/admin/ops/AdminPage";
import { PurchaseAiClient } from "@/components/admin/ops/PurchaseScreens";

export default function AdminPurchaseAiPage() {
  return (
    <AdminPage next="/admin/purchases/ai">
      <PurchaseAiClient />
    </AdminPage>
  );
}
