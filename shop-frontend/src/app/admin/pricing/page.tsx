import { AdminPage } from "@/components/admin/ops/AdminPage";
import { PricingClient } from "@/components/admin/ops/PricingClient";

export default function AdminPricingPage() {
  return (
    <AdminPage next="/admin/pricing">
      <PricingClient />
    </AdminPage>
  );
}
