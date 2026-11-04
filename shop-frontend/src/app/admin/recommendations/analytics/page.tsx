import { AdminPage } from "@/components/admin/ops/AdminPage";
import { RecoAnalyticsClient } from "@/components/admin/reco/RecoScreens";

export default function AdminRecoAnalyticsPage() {
  return (
    <AdminPage next="/admin/recommendations/analytics">
      <RecoAnalyticsClient />
    </AdminPage>
  );
}
