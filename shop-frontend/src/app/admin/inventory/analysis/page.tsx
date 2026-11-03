import { AdminPage } from "@/components/admin/ops/AdminPage";
import { StockAnalysisClient } from "@/components/admin/ops/InventoryScreens";

export default function AdminStockAnalysisPage() {
  return (
    <AdminPage next="/admin/inventory/analysis">
      <StockAnalysisClient />
    </AdminPage>
  );
}
