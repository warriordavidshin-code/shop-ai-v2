"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/Button";

type Filters = {
  page?: string;
  category?: string;
  keyword?: string;
  sort?: string;
  minPrice?: string;
  maxPrice?: string;
  color?: string;
  size?: string;
  availableOnly?: string;
};

export function ProductFilters({ current }: { current: Filters }) {
  const router = useRouter();
  const [keyword, setKeyword] = useState(current.keyword ?? "");
  const [sort, setSort] = useState(current.sort ?? "RECOMMENDED");
  const [open, setOpen] = useState(false);

  function apply(extra: Partial<Filters> = {}) {
    const next = { ...current, ...extra, keyword, sort, page: "0" };
    const qs = new URLSearchParams();
    Object.entries(next).forEach(([k, v]) => {
      if (v) qs.set(k, v);
    });
    router.push(`/products?${qs}`);
    setOpen(false);
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-col gap-3 md:flex-row md:items-center">
        <input
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="검색어"
          className="h-11 flex-1 rounded-xl border border-border bg-surface px-3 outline-none focus-visible:ring-2 focus-visible:ring-brand"
          aria-label="상품 검색어"
        />
        <select
          value={sort}
          onChange={(e) => setSort(e.target.value)}
          className="h-11 rounded-xl border border-border bg-surface px-3"
          aria-label="정렬"
        >
          <option value="RECOMMENDED">추천순</option>
          <option value="NEWEST">신상품순</option>
          <option value="PRICE_ASC">낮은 가격순</option>
          <option value="PRICE_DESC">높은 가격순</option>
        </select>
        <Button type="button" onClick={() => apply()}>
          적용
        </Button>
        <Button type="button" variant="secondary" className="md:hidden" onClick={() => setOpen(true)}>
          필터
        </Button>
      </div>

      <div className="hidden flex-wrap gap-2 md:flex">
        {["Ivory", "Camel", "Beige", "Cream"].map((color) => (
          <button
            key={color}
            type="button"
            onClick={() => apply({ color })}
            className="inline-flex h-11 items-center rounded-xl border border-border bg-surface px-4 text-sm hover:bg-surface-soft"
          >
            {color}
          </button>
        ))}
        <button
          type="button"
          onClick={() => apply({ availableOnly: "true" })}
          className="inline-flex h-11 items-center rounded-xl border border-border bg-surface px-4 text-sm hover:bg-surface-soft"
        >
          판매 가능만
        </button>
      </div>

      {open ? (
        <div className="fixed inset-0 z-50 bg-black/30 md:hidden" role="dialog" aria-modal>
          <div className="absolute inset-x-0 bottom-0 rounded-t-2xl bg-background p-5">
            <h2 className="mb-4 text-lg font-semibold">필터</h2>
            <div className="mb-4 flex flex-wrap gap-2">
              {["Ivory", "Camel", "Beige", "Cream"].map((color) => (
                <button
                  key={color}
                  type="button"
                  onClick={() => apply({ color })}
                  className="inline-flex h-11 items-center rounded-xl border border-border px-4 text-sm"
                >
                  {color}
                </button>
              ))}
            </div>
            <div className="flex gap-2">
              <Button variant="secondary" className="flex-1" onClick={() => setOpen(false)}>
                닫기
              </Button>
              <Button className="flex-1" onClick={() => apply({ availableOnly: "true" })}>
                판매 가능만
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
