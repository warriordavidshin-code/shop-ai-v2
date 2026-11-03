import { AdminPage } from "@/components/admin/ops/AdminPage";
import { PurchaseNewClient } from "@/components/admin/ops/PurchaseScreens";

export default function AdminPurchaseNewPage() {
  return (
    <AdminPage next="/admin/purchases/new">
      <PurchaseNewClient />
    </AdminPage>
  );
}
