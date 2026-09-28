import { redirect } from "next/navigation";
import { Container } from "@/components/ui/Container";
import { CancelRequestsClient } from "@/components/admin/CancelRequestsClient";
import { backendFetch } from "@/lib/api/server";

export default async function AdminCancelRequestsPage() {
  const probe = await backendFetch("/admin/order-cancel-requests?status=REQUESTED&page=0&size=1");
  if (probe.status === 401 || probe.status === 403) {
    redirect("/login?next=/admin/cancel-requests");
  }

  return (
    <main className="py-10">
      <Container className="flex flex-col gap-4">
        <div>
          <h1 className="heading-ko text-2xl">주문 취소 요청</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            결제 완료·상품 준비중 주문의 취소 요청을 검토합니다. 승인 시 재고가 복원되고 결제가 취소 처리됩니다.
          </p>
        </div>
        <CancelRequestsClient />
      </Container>
    </main>
  );
}
