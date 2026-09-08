import { cn } from "@/lib/utils";

export function FabricPlaceholder({
  className,
  label = "BoutiqueCamel",
  ratio = "4/5",
}: {
  className?: string;
  label?: string;
  ratio?: "4/5" | "16/9" | "1/1";
}) {
  const aspect =
    ratio === "16/9" ? "aspect-[16/9]" : ratio === "1/1" ? "aspect-square" : "aspect-[4/5]";

  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-xl bg-surface-warm",
        aspect,
        className,
      )}
      aria-hidden
    >
      <div
        className="absolute inset-0 opacity-70"
        style={{
          backgroundImage:
            "radial-gradient(circle at 20% 20%, #efe0d0 0%, transparent 45%), radial-gradient(circle at 80% 30%, #f8f3eb 0%, transparent 40%), linear-gradient(135deg, #f2e9dc 0%, #fdfcf9 55%, #efe0d0 100%)",
        }}
      />
      <div className="absolute inset-0 bg-[linear-gradient(90deg,rgba(255,255,255,0.15)_1px,transparent_1px)] bg-[length:12px_12px] opacity-40" />
      <span className="absolute bottom-3 left-3 text-xs tracking-wide text-muted-foreground">
        {label}
      </span>
    </div>
  );
}
