import { cn } from "@/lib/utils";
import { formatKrw } from "@/lib/format";
import { amountUntilFreeShipping, freeShippingMessage } from "@/features/orders/delivery";

type RowProps = {
  productAmount: number;
  deliveryAmount: number;
  as?: "div" | "dl-row";
  className?: string;
};

/** "배송비" line of an order summary; shows a 무료배송 tag when the fee was waived. */
export function DeliveryFeeRow({ productAmount, deliveryAmount, as = "div", className }: RowProps) {
  const free = productAmount > 0 && deliveryAmount === 0;
  const value = (
    <span className="flex items-center gap-2 tabular-nums">
      {free ? (
        <span className="rounded-full bg-brand px-2 py-0.5 text-[11px] font-medium text-white">무료배송</span>
      ) : null}
      {formatKrw(deliveryAmount)}원
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
export function FreeShippingNotice({ productAmount, className }: { productAmount: number; className?: string }) {
  const qualified = productAmount > 0 && amountUntilFreeShipping(productAmount) === 0;
  return (
    <p className={cn("text-xs", qualified ? "text-brand" : "text-muted-foreground", className)}>
      {freeShippingMessage(productAmount)}
    </p>
  );
}
