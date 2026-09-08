import { formatKrw } from "@/lib/format";
import { cn } from "@/lib/utils";

export function Price({
  salePrice,
  normalPrice,
  discountRate,
  className,
}: {
  salePrice: number;
  normalPrice?: number;
  discountRate?: number;
  className?: string;
}) {
  const hasDiscount = !!discountRate && discountRate > 0 && normalPrice && normalPrice > salePrice;
  return (
    <div className={cn("flex flex-wrap items-baseline gap-2 tabular-nums", className)}>
      {hasDiscount ? (
        <span className="text-sm font-semibold text-brand">{discountRate}%</span>
      ) : null}
      <span className="text-base font-semibold text-foreground">{formatKrw(salePrice)}원</span>
      {hasDiscount ? (
        <span className="text-sm text-muted-foreground line-through">
          {formatKrw(normalPrice)}원
        </span>
      ) : null}
    </div>
  );
}
