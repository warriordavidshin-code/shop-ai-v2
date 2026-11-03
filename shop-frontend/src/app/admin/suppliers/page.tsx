import { AdminPage } from "@/components/admin/ops/AdminPage";
import { SuppliersClient } from "@/components/admin/ops/SuppliersClient";

export default function AdminSuppliersPage() {
  return (
    <AdminPage next="/admin/suppliers">
      <SuppliersClient />
    </AdminPage>
  );
}
