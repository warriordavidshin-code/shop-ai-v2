import { OrdersClient } from "@/components/orders/OrdersClient";
import { freeShippingNotice } from "@/features/orders/delivery";
import { fetchShippingPolicyOnServer } from "@/features/shipping/server";
import { formatKrw } from "@/lib/format";

export default async function MyOrdersPage() {
  const policy = await fetchShippingPolicyOnServer();
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="heading-ko text-2xl text-foreground">주문내역</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          주문을 선택하면 배송조회, 주문 취소(요청), 반품 신청을 할 수 있습니다.
        </p>
        <p className="mt-2 inline-flex rounded-full bg-brand-soft px-3 py-1 text-xs font-medium text-brand">
          {freeShippingNotice(policy)} (미만 시 배송비 {formatKrw(policy.baseShippingFee)}원)
        </p>
      </div>
      <OrdersClient />
    </div>
  );
}
