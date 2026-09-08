import Link from "next/link";
import { Container } from "@/components/ui/Container";

export function Footer() {
  return (
    <footer className="mt-16 border-t border-border bg-surface-soft">
      <Container className="flex flex-col gap-4 py-10 text-sm text-muted-foreground">
        <Link href="/" className="text-base font-semibold text-foreground">
          BoutiqueCamel
        </Link>
        <p>편안한 데일리룩부터 특별한 날의 코디까지.</p>
        <div className="flex flex-wrap gap-4">
          <Link href="/products">상품</Link>
          <Link href="/style">AI 스타일리스트</Link>
          <Link href="/mypage">마이페이지</Link>
        </div>
        <p className="text-xs">© {new Date().getFullYear()} BoutiqueCamel</p>
      </Container>
    </footer>
  );
}
