import { AdminPage } from "@/components/admin/ops/AdminPage";
import { ProductProfitClient } from "@/components/admin/ops/AnalyticsScreens";

export default function AdminAnalyticsPage() {
  return (
    <AdminPage next="/admin/analytics">
      <ProductProfitClient />
    </AdminPage>
  );
}
