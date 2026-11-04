import { AdminPage } from "@/components/admin/ops/AdminPage";
import { AiSettingsClient } from "@/components/admin/ops/AiCostScreens";

export default function AdminAiSettingsPage() {
  return (
    <AdminPage next="/admin/ai/settings">
      <AiSettingsClient />
    </AdminPage>
  );
}
