import { AdminPage } from "@/components/admin/ops/AdminPage";
import { AiOperationsClient } from "@/components/admin/ops/AiScreens";

export default function AdminAiOperationsPage() {
  return (
    <AdminPage next="/admin/ai/operations">
      <AiOperationsClient />
    </AdminPage>
  );
}
