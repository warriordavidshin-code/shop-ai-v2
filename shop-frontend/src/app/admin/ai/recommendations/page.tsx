import { AdminPage } from "@/components/admin/ops/AdminPage";
import { RecommendationsClient } from "@/components/admin/ops/AiScreens";

export default function AdminAiRecommendationsPage() {
  return (
    <AdminPage next="/admin/ai/recommendations">
      <RecommendationsClient />
    </AdminPage>
  );
}
