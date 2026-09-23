import Link from "next/link";
import { BrandLogo } from "@/components/brand/BrandLogo";
import { HeaderAuthNav } from "@/components/layout/HeaderAuthNav";
import { Container } from "@/components/ui/Container";

const nav = [
  { href: "/products", label: "상품" },
  { href: "/search", label: "검색" },
  { href: "/style", label: "AI 스타일" },
  { href: "/wishlist", label: "찜" },
  { href: "/cart", label: "장바구니" },
];

export function Header() {
  return (
    <header className="sticky top-0 z-40 border-b border-border/80 bg-background/95 backdrop-blur">
      <Container className="flex h-14 items-center justify-between gap-3 md:h-[4.25rem] md:gap-8">
        <div className="flex min-w-0 items-center gap-5 md:gap-8">
          <BrandLogo size="header" priority />
          <nav className="hidden items-center gap-6 text-[13px] tracking-[0.04em] text-muted-foreground md:flex">
            {nav.map((item) => (
              <Link key={item.href} href={item.href} className="hover:text-foreground">
                {item.label}
              </Link>
            ))}
          </nav>
        </div>
        <nav className="hidden shrink-0 items-center gap-4 text-[13px] tracking-[0.02em] text-muted-foreground md:flex">
          <HeaderAuthNav />
        </nav>
      </Container>
    </header>
  );
}
