import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { DeliveryFeeRow } from "@/components/orders/DeliveryFee";
import { OrderCancelPanel } from "@/components/orders/OrderCancelPanel";
import { ReturnPanel } from "@/components/orders/ReturnPanel";
import { ShipmentTracker } from "@/components/orders/ShipmentTracker";
import { freeShippingNotice } from "@/features/orders/delivery";
import { OrderStatusBadge } from "@/components/orders/OrderStatusBadge";
import { orderSchema } from "@/features/orders/api";
import { formatDateTime } from "@/features/orders/status";
import { fetchShippingPolicyOnServer } from "@/features/shipping/server";
import { backendFetch } from "@/lib/api/server";
import { formatKrw } from "@/lib/format";

type Params = Promise<{ orderNo: string }>;

const AFTER_DELIVERY = ["DELIVERED", "RETURN_REQUESTED", "RETURNED"];

async function fetchOrder(orderNo: string) {
  const response = await backendFetch(`/orders/${encodeURIComponent(orderNo)}`);
  if (response.status === 401) return "unauthorized" as const;
  if (!response.ok) return null;
  return orderSchema.parse(await response.json());
}

export default async function OrderDetailPage({ params }: { params: Params }) {
  const { orderNo } = await params;
  const [order, policy] = await Promise.all([fetchOrder(orderNo), fetchShippingPolicyOnServer()]);
  if (order === "unauthorized") redirect(`/login?next=/mypage/orders/${orderNo}`);
  if (!order) notFound();

  return (
    <div className="flex max-w-2xl flex-col gap-5">
      <Link href="/mypage/orders" className="text-sm text-brand hover:underline">
        ← 주문내역
      </Link>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="heading-ko text-2xl text-foreground">주문 상세</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {order.orderNo} · {formatDateTime(order.orderedAt)}
          </p>
        </div>
        <OrderStatusBadge status={order.orderStatus} className="text-sm" />
      </div>

      {order.orderId ? <ShipmentTracker orderId={order.orderId} orderStatus={order.orderStatus} /> : null}

      <section className="rounded-xl border border-border bg-surface p-4">
        <h2 className="mb-2 text-base font-semibold">주문 상품</h2>
        <ul className="text-sm">
          {order.items.map((item, idx) => (
            <li
              key={`${item.skuId}-${idx}`}
              className="flex justify-between gap-3 border-b border-border py-2 last:border-b-0"
            >
              <span>
                {item.productName} / {item.optionName} × {item.quantity}
              </span>
              <span className="tabular-nums">{formatKrw(item.paymentPrice ?? item.unitPrice * item.quantity)}원</span>
            </li>
          ))}
        </ul>
        <dl className="mt-3 flex flex-col gap-1 border-t border-border pt-3 text-sm">
          <div className="flex justify-between">
            <dt className="text-muted-foreground">상품금액</dt>
            <dd className="tabular-nums">{formatKrw(order.totalProductAmount)}원</dd>
          </div>
          <DeliveryFeeRow
            as="dl-row"
            productAmount={order.totalProductAmount - order.discountAmount}
            deliveryAmount={order.deliveryAmount}
          />
          <div className="flex justify-between font-semibold">
            <dt>결제금액</dt>
            <dd className="tabular-nums">{formatKrw(order.paymentAmount)}원</dd>
          </div>
        </dl>
        <p className="mt-2 text-xs text-muted-foreground">{freeShippingNotice(policy)}</p>
      </section>

      <section className="rounded-xl border border-border bg-surface p-4 text-sm">
        <h2 className="mb-2 text-base font-semibold">배송지</h2>
        <p>
          {order.receiverName} · {order.receiverPhone}
        </p>
        <p className="mt-1 text-muted-foreground">
          ({order.postcode}) {order.address1} {order.address2}
        </p>
        {order.orderMemo ? <p className="mt-1 text-muted-foreground">요청사항: {order.orderMemo}</p> : null}
      </section>

      {order.orderId && AFTER_DELIVERY.includes(order.orderStatus) ? (
        <ReturnPanel
          orderId={order.orderId}
          orderStatus={order.orderStatus}
          paymentAmount={order.paymentAmount}
          receiverName={order.receiverName}
          receiverPhone={order.receiverPhone}
          postcode={order.postcode}
          address1={order.address1}
          address2={order.address2}
        />
      ) : (
        <OrderCancelPanel orderNo={order.orderNo} orderStatus={order.orderStatus} cancelRequest={order.cancelRequest} />
      )}
    </div>
  );
}
