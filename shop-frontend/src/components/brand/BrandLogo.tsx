import Image from "next/image";
import Link from "next/link";

type BrandLogoSize = "header" | "auth" | "footer" | "hero" | "admin";

type BrandLogoProps = {
  href?: string | null;
  size?: BrandLogoSize;
  className?: string;
  priority?: boolean;
};

/** Pre-rendered @3x assets so wordmarks stay sharp at each display size. */
const logoBySize: Record<
  BrandLogoSize,
  { src: string; width: number; height: number; className: string }
> = {
  header: {
    src: "/brand/logo-header@3x.png",
    width: 763,
    height: 108,
    className: "h-8 w-auto md:h-9",
  },
  auth: {
    src: "/brand/logo-auth@3x.png",
    width: 1186,
    height: 168,
    className: "h-11 w-auto md:h-14",
  },
  footer: {
    src: "/brand/logo-footer@3x.png",
    width: 847,
    height: 120,
    className: "h-9 w-auto md:h-10",
  },
  hero: {
    src: "/brand/logo-hero@3x.png",
    width: 1017,
    height: 144,
    className: "h-10 w-auto md:h-12",
  },
  admin: {
    src: "/brand/logo-admin@3x.png",
    width: 678,
    height: 96,
    className: "h-7 w-auto md:h-8",
  },
};

export function BrandLogo({
  href = "/",
  size = "header",
  className = "",
  priority = false,
}: BrandLogoProps) {
  const asset = logoBySize[size];
  const image = (
    <Image
      src={asset.src}
      alt="Boutique Camel"
      width={asset.width}
      height={asset.height}
      priority={priority}
      unoptimized
      className={`${asset.className} object-contain object-left ${className}`.trim()}
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
