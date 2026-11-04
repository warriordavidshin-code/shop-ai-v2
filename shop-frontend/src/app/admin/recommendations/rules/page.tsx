import { AdminPage } from "@/components/admin/ops/AdminPage";
import { RecoRulesClient } from "@/components/admin/reco/RecoScreens";

export default function AdminRecoRulesPage() {
  return (
    <AdminPage next="/admin/recommendations/rules">
      <RecoRulesClient />
    </AdminPage>
  );
}
