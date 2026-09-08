import Link from "next/link";
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
      <Container className="flex h-14 items-center justify-between gap-4 md:h-16">
        <Link href="/" className="text-lg font-semibold tracking-tight text-foreground">
          BoutiqueCamel
        </Link>
        <nav className="hidden items-center gap-5 text-sm text-muted-foreground md:flex">
          {nav.map((item) => (
            <Link key={item.href} href={item.href} className="hover:text-foreground">
              {item.label}
            </Link>
          ))}
          <Link href="/login" className="text-foreground hover:underline">
            로그인
          </Link>
          <Link href="/mypage" className="text-foreground hover:underline">
            마이페이지
          </Link>
        </nav>
      </Container>
    </header>
  );
}
