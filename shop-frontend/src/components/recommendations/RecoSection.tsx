"use client";

import { useEffect, useRef, useState } from "react";
import { ProductCard } from "@/components/product/ProductCard";
import { fetchRecommendations, type RecoList, sendRecoEvent } from "@/features/recommendations/api";

/**
 * Client-side recommendation strip: loads after the page (so personalization uses the visitor's cookies),
 * reports one IMPRESSION when it scrolls into view and a CLICK per product, and hides itself when empty or failing.
 */
export function RecoSection({
  path,
  title,
  limit = 8,
  columns = 4,
  actions,
  className = "",
}: {
  path: string;
  title?: string;
  limit?: number;
  columns?: 2 | 3 | 4;
  actions?: React.ReactNode;
  className?: string;
}) {
  const [data, setData] = useState<RecoList | null>(null);
  const ref = useRef<HTMLElement | null>(null);
  const seen = useRef<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const sep = path.includes("?") ? "&" : "?";
    fetchRecommendations(`${path}${sep}limit=${limit}`).then((result) => {
      if (!cancelled) setData(result);
    });
    return () => {
      cancelled = true;
    };
  }, [path, limit]);

  useEffect(() => {
    const el = ref.current;
    const requestId = data?.requestId ?? null;
    if (!el || !requestId || data?.items.length === 0 || seen.current === requestId) return;
    if (typeof IntersectionObserver === "undefined") {
      seen.current = requestId;
      sendRecoEvent(requestId, "IMPRESSION", undefined, data?.items.length);
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting) && seen.current !== requestId) {
          seen.current = requestId;
          sendRecoEvent(requestId, "IMPRESSION", undefined, data?.items.length);
          observer.disconnect();
        }
      },
      { threshold: 0.3 },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [data]);

  if (!data || data.items.length === 0) return null;

  const grid = columns === 2 ? "grid-cols-2" : columns === 3 ? "grid-cols-2 md:grid-cols-3" : "grid-cols-2 md:grid-cols-3 lg:grid-cols-4";

  return (
    <section ref={ref} className={`flex flex-col gap-4 ${className}`} data-placement={data.placement}>
      <div className="flex items-end justify-between gap-3">
        <h2 className="heading-ko text-xl text-foreground">{title ?? data.title}</h2>
        {actions}
      </div>
      <div className={`grid gap-x-4 gap-y-8 ${grid}`}>
        {data.items.map((item) => {
          const requestId = data.requestId;
          const href = requestId
            ? `/products/${item.product.productId}?rec=${encodeURIComponent(requestId)}`
            : `/products/${item.product.productId}`;
          return (
            <ProductCard
              key={item.product.productId}
              product={item.product}
              href={href}
              note={item.reason}
              onNavigate={() => sendRecoEvent(requestId, "CLICK", item.product.productId)}
            />
          );
        })}
      </div>
    </section>
  );
}

/** 인기 상품 with a 7일/30일 toggle. */
export function PopularRecoSection({ type = "TOP10", className = "" }: { type?: string; className?: string }) {
  const [period, setPeriod] = useState(7);
  return (
    <RecoSection
      key={period}
      path={`popular?type=${type}&period=${period}`}
      className={className}
      actions={
        <div className="flex gap-1 text-sm" role="group" aria-label="집계 기간">
          {[7, 30].map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => setPeriod(p)}
              className={`rounded-md px-3 py-1 ${period === p ? "bg-brand text-white" : "border border-border text-muted-foreground hover:bg-surface-soft"}`}
            >
              {p}일
            </button>
          ))}
        </div>
      }
    />
  );
}
