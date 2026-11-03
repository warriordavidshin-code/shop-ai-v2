import { AdminPage } from "@/components/admin/ops/AdminPage";
import { MonthlyProfitClient } from "@/components/admin/ops/AnalyticsScreens";

export default function AdminMonthlyProfitPage() {
  return (
    <AdminPage next="/admin/analytics/monthly">
      <MonthlyProfitClient />
    </AdminPage>
  );
}
