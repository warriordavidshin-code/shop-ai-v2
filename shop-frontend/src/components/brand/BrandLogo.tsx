import Image from "next/image";
import Link from "next/link";

type BrandLogoProps = {
  href?: string | null;
  /** Visual size preset */
  size?: "header" | "auth" | "footer" | "hero";
  className?: string;
  priority?: boolean;
};

const sizeClass: Record<NonNullable<BrandLogoProps["size"]>, string> = {
  header: "h-9 w-auto md:h-11",
  auth: "h-12 w-auto md:h-14",
  footer: "h-10 w-auto",
  hero: "h-10 w-auto md:h-12",
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
