"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { BrandLogo } from "@/components/brand/BrandLogo";

type NavLink = { href: string; label: string; exact?: boolean };
type NavGroup = { label: string; links: NavLink[] };

export const adminNavGroups: NavGroup[] = [
  {
    label: "대시보드",
    links: [
      { href: "/admin", label: "운영 현황", exact: true },
      { href: "/admin/operations", label: "통합 대시보드" },
    ],
  },
  {
    label: "상품관리",
    links: [
      { href: "/admin/products", label: "상품 목록" },
      { href: "/admin/products/new", label: "상품 등록 (AI)" },
      { href: "/admin/banners", label: "메인 배너" },
      { href: "/admin/reviews", label: "리뷰" },
    ],
  },
  {
    label: "매입관리",
    links: [
      { href: "/admin/purchases", label: "매입장부" },
      { href: "/admin/purchases/new", label: "매입 등록" },
      { href: "/admin/purchases/ai", label: "AI 영수증 분석" },
      { href: "/admin/purchases/matching", label: "상품 매칭" },
      { href: "/admin/purchases/summary", label: "월별 현황" },
      { href: "/admin/purchases/evidence", label: "증빙 관리" },
      { href: "/admin/suppliers", label: "거래처" },
    ],
  },
  {
    label: "재고관리",
    links: [
      { href: "/admin/inventory", label: "옵션 재고 수정" },
      { href: "/admin/inventory/status", label: "재고 현황" },
      { href: "/admin/inventory/receipts", label: "입고 관리" },
      { href: "/admin/inventory/transactions", label: "재고 수불부" },
      { href: "/admin/inventory/analysis", label: "부족·장기 재고" },
    ],
  },
  {
    label: "주문·배송",
    links: [
      { href: "/admin/orders", label: "주문" },
      { href: "/admin/cancel-requests", label: "취소 요청" },
      { href: "/admin/returns", label: "반품 관리" },
      { href: "/admin/shipping-settings", label: "배송 설정" },
    ],
  },
  {
    label: "판매분석",
    links: [
      { href: "/admin/analytics", label: "상품별 손익" },
      { href: "/admin/analytics/monthly", label: "월별 손익" },
      { href: "/admin/pricing", label: "가격 정책" },
    ],
  },
  {
    label: "AI 운영관리",
    links: [
      { href: "/admin/ai", label: "AI MD 통계" },
      { href: "/admin/ai/operations", label: "분석 이력·사용량" },
      { href: "/admin/ai/recommendations", label: "AI 발주 추천" },
    ],
  },
  {
    label: "회원",
    links: [{ href: "/admin/members", label: "회원" }],
  },
];

function matches(pathname: string, link: NavLink): boolean {
  return link.exact ? pathname === link.href : pathname === link.href || pathname.startsWith(`${link.href}/`);
}

/** Most specific link wins so /admin/products/new is not also highlighted as /admin/products. */
export function activeLink(pathname: string): NavLink | null {
  let best: NavLink | null = null;
  for (const group of adminNavGroups) {
    for (const link of group.links) {
      if (matches(pathname, link) && (!best || link.href.length > best.href.length)) {
        best = link;
      }
    }
  }
  return best;
}

export function AdminNav() {
  const pathname = usePathname();
  const active = activeLink(pathname);
  const activeGroup =
    adminNavGroups.find((group) => group.links.some((link) => link === active)) ?? adminNavGroups[0];

  return (
    <header className="border-b border-border bg-surface/95 backdrop-blur">
      <div className="mx-auto flex max-w-6xl flex-col gap-3 px-4 py-4 sm:px-6">
        <div className="flex items-center justify-between gap-3">
          <div className="flex min-w-0 items-center gap-3">
            <BrandLogo size="admin" href="/admin" priority />
            <span className="heading-en hidden text-sm tracking-[0.12em] text-muted-foreground sm:inline">
              ADMIN
            </span>
          </div>
          <div className="flex items-center gap-3">
            <a
              href="/manuals/operator"
              target="_blank"
              rel="noopener"
              className="text-sm text-muted-foreground hover:text-foreground"
            >
              운영자 매뉴얼
            </a>
            <Link href="/" className="text-sm text-muted-foreground hover:text-foreground">
              쇼핑몰로
            </Link>
          </div>
        </div>
        <nav className="flex flex-wrap gap-1" aria-label="관리자 메뉴 그룹">
          {adminNavGroups.map((group) => (
            <Link
              key={group.label}
              href={group.links[0].href}
              className={[
                "rounded-md px-3 py-2 text-sm transition-colors",
                group === activeGroup
                  ? "bg-brand text-white"
                  : "text-muted-foreground hover:bg-surface-soft hover:text-foreground",
              ].join(" ")}
            >
              {group.label}
            </Link>
          ))}
        </nav>
        <nav className="flex flex-wrap gap-1 border-t border-border pt-2" aria-label={`${activeGroup.label} 하위 메뉴`}>
          {activeGroup.links.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className={[
                "rounded-md px-3 py-1.5 text-sm transition-colors",
                link === active
                  ? "bg-surface-soft font-medium text-foreground"
                  : "text-muted-foreground hover:text-foreground",
              ].join(" ")}
            >
              {link.label}
            </Link>
          ))}
        </nav>
      </div>
    </header>
  );
}
