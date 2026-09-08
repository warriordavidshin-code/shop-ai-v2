"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ProductGrid } from "@/components/product/ProductCard";
import { ApiError } from "@/lib/api/client";
import { getWishlist, removeWishlist } from "@/features/wishlist/api";
import type { ProductSummary } from "@/features/products/api";
import { Button } from "@/components/ui/Button";

export function WishlistClient() {
  const [items, setItems] = useState<ProductSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      setItems(await getWishlist());
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        setError("로그인 후 찜 목록을 확인할 수 있습니다.");
      } else {
        setError(e instanceof ApiError ? e.message : "찜 목록을 불러오지 못했습니다.");
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  if (loading) return <p className="text-sm text-muted-foreground">불러오는 중...</p>;
  if (error) {
    return (
      <div className="flex flex-col gap-3">
        <p className="text-sm text-danger">{error}</p>
        <Link href="/login?next=/wishlist" className="text-sm text-brand hover:underline">
          로그인하기
        </Link>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <ProductGrid products={items} />
      {items.length > 0 ? (
        <ul className="flex flex-col gap-2">
          {items.map((item) => (
            <li key={item.productId} className="flex items-center justify-between text-sm">
              <span>{item.productName}</span>
              <Button
                variant="secondary"
                onClick={async () => {
                  await removeWishlist(item.productId);
                  await load();
                }}
              >
                찜 해제
              </Button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
