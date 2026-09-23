import { HeaderAuthNav } from "@/components/layout/HeaderAuthNav";
import { BrandLogo } from "@/components/brand/BrandLogo";
import { Container } from "@/components/ui/Container";
import Link from "next/link";

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
      <Container className="flex h-14 items-center justify-between gap-4 md:h-16">
        <nav className="hidden items-center gap-5 text-sm text-muted-foreground md:flex">
          {nav.map((item) => (
            <Link key={item.href} href={item.href} className="hover:text-foreground">
              {item.label}
            </Link>
          ))}
        </nav>
        <div className="ml-auto flex items-center gap-4 md:gap-5">
          <BrandLogo size="header" priority />
          <nav className="hidden items-center gap-4 text-sm text-muted-foreground md:flex">
            <HeaderAuthNav />
          </nav>
        </div>
      </Container>
    </header>
  );
}
