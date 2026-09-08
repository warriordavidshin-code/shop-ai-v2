import { cn } from "@/lib/utils";

type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost";
};

export function Button({
  className,
  variant = "primary",
  type = "button",
  ...props
}: ButtonProps) {
  const styles =
    variant === "primary"
      ? "bg-brand text-white hover:bg-brand-hover"
      : variant === "secondary"
        ? "border border-border bg-surface text-foreground hover:bg-surface-soft"
        : "bg-transparent text-foreground hover:bg-surface-soft";

  return (
    <button
      type={type}
      className={cn(
        "inline-flex h-11 min-w-11 items-center justify-center rounded-xl px-5 text-sm font-medium transition-colors duration-200 disabled:opacity-60",
        styles,
        className,
      )}
      {...props}
    />
  );
}
