import { Footer } from "@/components/layout/Footer";
import { Header } from "@/components/layout/Header";
import { MobileBottomNav } from "@/components/layout/MobileBottomNav";

export default function ShopLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-background pb-16 md:pb-0">
      <div className="bg-surface-warm px-4 py-2 text-center text-xs text-muted-foreground md:text-sm">
        봄 신상품과 AI 코디 추천을 만나보세요
      </div>
      <Header />
      <div className="flex-1">{children}</div>
      <Footer />
      <MobileBottomNav />
    </div>
  );
}
