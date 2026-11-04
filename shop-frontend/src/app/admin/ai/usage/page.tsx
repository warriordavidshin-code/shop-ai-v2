import { AdminPage } from "@/components/admin/ops/AdminPage";
import { AiUsageClient } from "@/components/admin/ops/AiCostScreens";

export default function AdminAiUsagePage() {
  return (
    <AdminPage next="/admin/ai/usage">
      <AiUsageClient />
    </AdminPage>
  );
}
