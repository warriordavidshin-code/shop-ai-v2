import { cn } from "@/lib/utils";
import { orderStatusLabel, orderStatusTone } from "@/features/orders/status";

const toneClass = {
  neutral: "border-border bg-surface text-foreground",
  brand: "border-brand/30 bg-brand-soft text-brand",
  warning: "border-accent/40 bg-accent-soft text-foreground",
  muted: "border-border bg-surface-soft text-muted-foreground",
} as const;

export function OrderStatusBadge({ status, className }: { status: string; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium",
        toneClass[orderStatusTone(status)],
        className,
      )}
    >
      {orderStatusLabel(status)}
    </span>
  );
}
