"use client";

import { useEffect, useState } from "react";
import { FabricPlaceholder } from "@/components/ui/FabricPlaceholder";
import { resolveMediaUrl } from "@/lib/media";

type Banner = {
  bannerId: number;
  imageUrl: string;
  overlayText: string | null;
};

export function HomeHeroBanner({ banners }: { banners: Banner[] }) {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    if (banners.length <= 1) return;
    const timer = setInterval(() => {
      setIndex((prev) => (prev + 1) % banners.length);
    }, 2000);
    return () => clearInterval(timer);
  }, [banners.length]);

  if (!banners.length) {
    return <FabricPlaceholder ratio="16/9" label="자연광 · 린넨" className="w-full" />;
  }

  const current = banners[Math.min(index, banners.length - 1)];

  return (
    <div className="relative aspect-video w-full overflow-hidden rounded-xl">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={resolveMediaUrl(current.imageUrl)}
        alt={current.overlayText ?? "hero"}
        className="h-full w-full object-cover transition-opacity duration-500"
      />
      {current.overlayText ? (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/25 px-6 text-center">
          <p className="max-w-md text-lg font-semibold text-white drop-shadow md:text-2xl">
            {current.overlayText}
          </p>
        </div>
      ) : null}
    </div>
  );
}
