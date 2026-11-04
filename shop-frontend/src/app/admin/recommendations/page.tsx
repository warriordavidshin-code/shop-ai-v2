import { AdminPage } from "@/components/admin/ops/AdminPage";
import { RecoSettingsClient } from "@/components/admin/reco/RecoScreens";

export default function AdminRecoSettingsPage() {
  return (
    <AdminPage next="/admin/recommendations">
      <RecoSettingsClient />
    </AdminPage>
  );
}
