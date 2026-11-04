import { AdminPage } from "@/components/admin/ops/AdminPage";
import { RecoPurchaseClient } from "@/components/admin/reco/RecoScreens";

export default function AdminRecoPurchasePage() {
  return (
    <AdminPage next="/admin/recommendations/purchase">
      <RecoPurchaseClient />
    </AdminPage>
  );
}
