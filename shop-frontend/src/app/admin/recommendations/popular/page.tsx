import { AdminPage } from "@/components/admin/ops/AdminPage";
import { RecoPopularClient } from "@/components/admin/reco/RecoScreens";

export default function AdminRecoPopularPage() {
  return (
    <AdminPage next="/admin/recommendations/popular">
      <RecoPopularClient />
    </AdminPage>
  );
}
