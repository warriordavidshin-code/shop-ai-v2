import { Container } from "@/components/ui/Container";
import { MypageNav } from "@/components/mypage/MypageNav";

export default function MypageLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className="py-8 md:py-12">
      <Container className="grid gap-6 md:grid-cols-[180px_1fr] md:gap-10">
        <aside className="flex flex-col gap-3">
          <p className="heading-ko hidden text-lg text-foreground md:block">마이페이지</p>
          <MypageNav />
        </aside>
        <div className="min-w-0">{children}</div>
      </Container>
    </main>
  );
}
