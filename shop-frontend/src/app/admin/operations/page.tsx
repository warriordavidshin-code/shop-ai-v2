import { AdminPage } from "@/components/admin/ops/AdminPage";
import { OperationsDashboardClient } from "@/components/admin/ops/AnalyticsScreens";

export default function AdminOperationsDashboardPage() {
  return (
    <AdminPage next="/admin/operations">
      <OperationsDashboardClient />
    </AdminPage>
  );
}
