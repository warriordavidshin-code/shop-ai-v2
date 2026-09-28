import { OrdersClient } from "@/components/orders/OrdersClient";

export default function MyOrdersPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="heading-ko text-2xl text-foreground">주문내역</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          주문을 선택하면 상세 내역 확인과 주문 취소(요청)를 할 수 있습니다.
        </p>
      </div>
      <OrdersClient />
    </div>
  );
}
