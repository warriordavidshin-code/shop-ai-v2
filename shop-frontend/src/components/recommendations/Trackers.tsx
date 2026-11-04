"use client";

import { useEffect } from "react";
import { rememberRecoSource, trackProductView, trackSearch } from "@/features/recommendations/api";

export function ProductViewTracker({ productId }: { productId: number }) {
  useEffect(() => {
    const rec = new URLSearchParams(window.location.search).get("rec");
    if (rec) rememberRecoSource(productId, rec);
    trackProductView(productId);
  }, [productId]);
  return null;
}

export function SearchTracker({ keyword }: { keyword?: string }) {
  useEffect(() => {
    if (keyword) trackSearch(keyword);
  }, [keyword]);
  return null;
}
