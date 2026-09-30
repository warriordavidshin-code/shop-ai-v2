import { cn } from "@/lib/utils";
import { formatKrw } from "@/lib/format";
import {
  amountUntilFreeShipping,
  DEFAULT_SHIPPING_POLICY,
  freeShippingMessage,
  type ShippingPolicy,
} from "@/features/orders/delivery";

type RowProps = {
  productAmount: number;
  deliveryAmount: number;
  /** Jeju / remote-island surcharge included in deliveryAmount, shown as a note. */
  extraFee?: number;
  as?: "div" | "dl-row";
  className?: string;
};

/** "배송비" line of an order summary; shows "무료" when the fee was waived. */
export function DeliveryFeeRow({ productAmount, deliveryAmount, extraFee = 0, as = "div", className }: RowProps) {
  const free = productAmount > 0 && deliveryAmount === 0;
  const value = (
    <span className="flex items-center gap-2 tabular-nums">
      {free ? (
        <span className="font-semibold text-brand">무료</span>
      ) : (
        <>
          {extraFee > 0 ? (
            <span className="text-xs text-muted-foreground">(도서산간 {formatKrw(extraFee)}원 포함)</span>
          ) : null}
          {formatKrw(deliveryAmount)}원
        </>
      )}
    </span>
  );

  if (as === "dl-row") {
    return (
      <div className={cn("flex items-center justify-between", className)}>
        <dt className="text-muted-foreground">배송비</dt>
        <dd>{value}</dd>
      </div>
    );
  }
  return (
    <div className={cn("flex items-center justify-between py-1", className)}>
      <span>배송비</span>
      {value}
    </div>
  );
}

/** Explains the free-shipping rule and how much more is needed to qualify. */
export function FreeShippingNotice({
  productAmount,
  policy = DEFAULT_SHIPPING_POLICY,
  className,
}: {
  productAmount: number;
  policy?: ShippingPolicy;
  className?: string;
}) {
  const qualified = productAmount > 0 && amountUntilFreeShipping(productAmount, policy) === 0;
  return (
    <p className={cn("text-xs", qualified ? "text-brand" : "text-muted-foreground", className)}>
      {freeShippingMessage(productAmount, policy)}
    </p>
  );
}
