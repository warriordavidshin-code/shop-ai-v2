import { Footer } from "@/components/layout/Footer";
import { Header } from "@/components/layout/Header";
import { MobileBottomNav } from "@/components/layout/MobileBottomNav";

export default function ShopLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-background pb-16 md:pb-0">
      <div className="border-b border-border/60 bg-surface-warm px-4 py-2 text-right text-[11px] tracking-[0.02em] text-muted-foreground md:text-xs">
        일상을 특별하게, BoutiqueCamel · 5만원 이상 구매 시 무료배송
      </div>
      <Header />
      <div className="flex-1">{children}</div>
      <Footer />
      <MobileBottomNav />
    </div>
  );
}
