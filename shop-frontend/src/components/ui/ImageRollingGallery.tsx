"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { FabricPlaceholder } from "@/components/ui/FabricPlaceholder";
import { resolveMediaUrl } from "@/lib/media";

type Props = {
  images: Array<{ url: string; alt?: string | null }>;
  placeholderLabel?: string;
  ratioClassName?: string;
  className?: string;
  /** delay before rolling starts on hover (ms) */
  hoverDelayMs?: number;
  /** interval between slides while hovering (ms) */
  intervalMs?: number;
  autoPlay?: boolean;
  overlayText?: string | null;
};

export function ImageRollingGallery({
  images,
  placeholderLabel = "BoutiqueCamel",
  ratioClassName = "aspect-[4/5]",
  className = "",
  hoverDelayMs = 2000,
  intervalMs = 2000,
  autoPlay = false,
  overlayText,
}: Props) {
  const urls = useMemo(
    () => images.map((item) => resolveMediaUrl(item.url)).filter(Boolean),
    [images],
  );
  const imageKey = urls.join("|");
  const [index, setIndex] = useState(0);
  const [hovering, setHovering] = useState(false);
  const delayTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const rollTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    setIndex(0);
  }, [imageKey]);

  useEffect(() => {
    function clearTimers() {
      if (delayTimer.current) clearTimeout(delayTimer.current);
      if (rollTimer.current) clearInterval(rollTimer.current);
      delayTimer.current = null;
      rollTimer.current = null;
    }

    clearTimers();
    if (urls.length <= 1) return;

    if (autoPlay) {
      rollTimer.current = setInterval(() => {
        setIndex((prev) => (prev + 1) % urls.length);
      }, intervalMs);
      return clearTimers;
    }

    if (hovering) {
      delayTimer.current = setTimeout(() => {
        rollTimer.current = setInterval(() => {
          setIndex((prev) => (prev + 1) % urls.length);
        }, intervalMs);
      }, hoverDelayMs);
    } else {
      setIndex(0);
    }

    return clearTimers;
  }, [autoPlay, hovering, hoverDelayMs, intervalMs, urls.length]);

  if (urls.length === 0) {
    return <FabricPlaceholder label={placeholderLabel} className={className} />;
  }

  const current = images[Math.min(index, images.length - 1)];

  return (
    <div
      className={`relative overflow-hidden rounded-xl ${ratioClassName} ${className}`}
      onMouseEnter={() => setHovering(true)}
      onMouseLeave={() => setHovering(false)}
    >
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={urls[index]}
        alt={current?.alt ?? placeholderLabel}
        className="h-full w-full object-cover transition-opacity duration-500"
      />
      {overlayText ? (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/25 px-6 text-center">
          <p className="max-w-md text-lg font-semibold text-white drop-shadow md:text-2xl">{overlayText}</p>
        </div>
      ) : null}
    </div>
  );
}
