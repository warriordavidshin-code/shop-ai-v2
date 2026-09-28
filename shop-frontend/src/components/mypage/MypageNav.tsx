"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

const links = [
  { href: "/mypage", label: "마이페이지 홈", exact: true },
  { href: "/mypage/orders", label: "주문내역" },
  { href: "/mypage/addresses", label: "배송지 관리" },
  { href: "/mypage/verify", label: "개인정보 수정", match: ["/mypage/verify", "/mypage/profile"] },
];

export function MypageNav() {
  const pathname = usePathname();

  return (
    <nav aria-label="마이페이지 메뉴" className="flex gap-1 overflow-x-auto md:flex-col">
      {links.map((link) => {
        const prefixes = link.match ?? [link.href];
        const active = link.exact
          ? pathname === link.href
          : prefixes.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
        return (
          <Link
            key={link.href}
            href={link.href}
            className={cn(
              "shrink-0 rounded-lg px-3 py-2 text-sm transition-colors",
              active
                ? "bg-brand text-white"
                : "text-muted-foreground hover:bg-surface-soft hover:text-foreground",
            )}
          >
            {link.label}
          </Link>
        );
      })}
    </nav>
  );
}
