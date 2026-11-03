import { AdminPage } from "@/components/admin/ops/AdminPage";
import { PurchaseSummaryClient } from "@/components/admin/ops/PurchaseInsights";

export default function AdminPurchaseSummaryPage() {
  return (
    <AdminPage next="/admin/purchases/summary">
      <PurchaseSummaryClient />
    </AdminPage>
  );
}
