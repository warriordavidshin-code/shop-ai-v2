import Link from "next/link";
import { Heart, Home, Search, ShoppingBag, Sparkles } from "lucide-react";

const items = [
  { href: "/", label: "홈", icon: Home },
  { href: "/products", label: "상품", icon: Search },
  { href: "/style", label: "AI", icon: Sparkles },
  { href: "/wishlist", label: "찜", icon: Heart },
  { href: "/cart", label: "장바구니", icon: ShoppingBag },
];

export function MobileBottomNav() {
  return (
    <nav
      className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-background/95 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden"
      aria-label="하단 내비게이션"
    >
      <ul className="grid grid-cols-5">
        {items.map(({ href, label, icon: Icon }) => (
          <li key={href}>
            <Link
              href={href}
              className="flex min-h-14 flex-col items-center justify-center gap-1 text-[11px] text-muted-foreground hover:text-foreground"
            >
              <Icon size={20} aria-hidden />
              <span>{label}</span>
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
