import { AdminPage } from "@/components/admin/ops/AdminPage";
import { PurchaseLedgerList } from "@/components/admin/ops/PurchaseLedgerList";

export default async function AdminPurchasesPage({
  searchParams,
}: {
  searchParams: Promise<{ month?: string; supplier?: string; verification?: string }>;
}) {
  const { month, supplier, verification } = await searchParams;
  return (
    <AdminPage next="/admin/purchases">
      <PurchaseLedgerList
        initial={{
          month: month && /^\d{4}-\d{2}$/.test(month) ? month : "",
          supplier: supplier ?? "",
          verification: verification === "NEEDS_REVIEW" || verification === "BASIC_COMPLETE" ? verification : "",
        }}
      />
    </AdminPage>
  );
}
