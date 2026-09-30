import { Container } from "@/components/ui/Container";
import { ShippingSettingsClient } from "@/components/admin/ShippingSettingsClient";
import { requireAdmin } from "@/features/admin/guard";

export default async function AdminShippingSettingsPage() {
  await requireAdmin("/admin/shipping/policy", "/admin/shipping-settings");

  return (
    <main className="py-10">
      <Container className="flex flex-col gap-4">
        <div>
          <h1 className="heading-ko text-2xl">배송 설정</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            배송비 정책, 제주·도서산간 지역, 사용 택배사와 배송조회 API 연동 상태를 관리합니다.
          </p>
        </div>
        <ShippingSettingsClient />
      </Container>
    </main>
  );
}
