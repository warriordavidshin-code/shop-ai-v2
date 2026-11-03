import { AdminPage } from "@/components/admin/ops/AdminPage";
import { StockStatusClient } from "@/components/admin/ops/InventoryScreens";

export default function AdminStockStatusPage() {
  return (
    <AdminPage next="/admin/inventory/status">
      <StockStatusClient />
    </AdminPage>
  );
}
