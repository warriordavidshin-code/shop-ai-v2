import { AdminPage } from "@/components/admin/ops/AdminPage";
import { RecoSlowMovingClient } from "@/components/admin/reco/RecoScreens";

export default function AdminRecoSlowMovingPage() {
  return (
    <AdminPage next="/admin/recommendations/slow-moving">
      <RecoSlowMovingClient />
    </AdminPage>
  );
}
