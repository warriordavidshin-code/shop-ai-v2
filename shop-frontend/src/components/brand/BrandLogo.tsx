import Image from "next/image";
import Link from "next/link";

type BrandLogoProps = {
  href?: string | null;
  /** Visual size preset */
  size?: "header" | "auth" | "footer" | "hero";
  className?: string;
  priority?: boolean;
};

/**
 * Wide camel+wordmark mark (≈7:1). Cap height and max-width per surface
 * so it stays crisp without crowding nav or auth forms.
 */
const sizeClass: Record<NonNullable<BrandLogoProps["size"]>, string> = {
  header: "h-7 w-auto max-w-[160px] sm:h-8 sm:max-w-[190px] md:h-9 md:max-w-[220px]",
  auth: "h-10 w-auto max-w-[240px] md:h-12 md:max-w-[300px]",
  footer: "h-8 w-auto max-w-[180px]",
  hero: "h-9 w-auto max-w-[220px] md:h-11 md:max-w-[280px]",
};

export function BrandLogo({
  href = "/",
  size = "header",
  className = "",
  priority = false,
}: BrandLogoProps) {
  const image = (
    <Image
      src="/logo-boutique-camel.png"
      alt="Boutique Camel"
      width={1024}
      height={145}
      priority={priority}
      className={`${sizeClass[size]} object-contain object-left ${className}`.trim()}
    />
  );

  if (!href) {
    return image;
  }

  return (
    <Link href={href} className="inline-flex shrink-0 items-center" aria-label="Boutique Camel 홈">
      {image}
    </Link>
  );
}
