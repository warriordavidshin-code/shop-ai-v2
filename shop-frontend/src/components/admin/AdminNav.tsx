"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const links = [
  { href: "/admin", label: "대시보드", exact: true },
  { href: "/admin/products", label: "상품" },
  { href: "/admin/inventory", label: "재고" },
  { href: "/admin/orders", label: "주문" },
  { href: "/admin/members", label: "회원" },
  { href: "/admin/reviews", label: "리뷰" },
  { href: "/admin/ai", label: "AI MD" },
  { href: "/admin/banners", label: "메인 배너" },
];

export function AdminNav() {
  const pathname = usePathname();

  return (
    <header className="border-b border-border bg-surface">
      <div className="mx-auto flex max-w-6xl flex-col gap-3 px-4 py-4 sm:px-6">
        <div className="flex items-center justify-between gap-3">
          <Link href="/admin" className="text-lg font-semibold tracking-tight text-foreground">
            BoutiqueCamel Admin
          </Link>
          <Link href="/" className="text-sm text-muted-foreground hover:text-foreground">
            쇼핑몰로
          </Link>
        </div>
        <nav className="flex flex-wrap gap-1">
          {links.map((link) => {
            const active = link.exact
              ? pathname === link.href
              : pathname === link.href || pathname.startsWith(`${link.href}/`);
            return (
              <Link
                key={link.href}
                href={link.href}
                className={[
                  "rounded-lg px-3 py-2 text-sm transition-colors",
                  active
                    ? "bg-brand text-white"
                    : "text-muted-foreground hover:bg-surface-soft hover:text-foreground",
                ].join(" ")}
              >
                {link.label}
              </Link>
            );
          })}
        </nav>
      </div>
    </header>
  );
}
