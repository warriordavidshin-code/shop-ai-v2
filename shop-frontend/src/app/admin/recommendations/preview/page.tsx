import { AdminPage } from "@/components/admin/ops/AdminPage";
import { RecoPreviewClient } from "@/components/admin/reco/RecoScreens";

export default function AdminRecoPreviewPage() {
  return (
    <AdminPage next="/admin/recommendations/preview">
      <RecoPreviewClient />
    </AdminPage>
  );
}
