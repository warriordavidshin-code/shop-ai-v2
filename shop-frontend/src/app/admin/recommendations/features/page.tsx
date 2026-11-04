import { AdminPage } from "@/components/admin/ops/AdminPage";
import { RecoFeaturesClient } from "@/components/admin/reco/RecoScreens";

export default function AdminRecoFeaturesPage() {
  return (
    <AdminPage next="/admin/recommendations/features">
      <RecoFeaturesClient />
    </AdminPage>
  );
}
