import { AdminPage } from "@/components/admin/ops/AdminPage";
import { EvidenceManagerClient } from "@/components/admin/ops/PurchaseInsights";

export default function AdminPurchaseEvidencePage() {
  return (
    <AdminPage next="/admin/purchases/evidence">
      <EvidenceManagerClient />
    </AdminPage>
  );
}
