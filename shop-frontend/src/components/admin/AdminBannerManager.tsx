"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";
import { resolveMediaUrl } from "@/lib/media";

type Banner = {
  bannerId: number;
  imageUrl: string;
  overlayText: string | null;
  sortOrder: number;
  active: boolean;
};

function getCsrfToken(): string | undefined {
  if (typeof document === "undefined") return undefined;
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : undefined;
}

export function AdminBannerManager({ initial }: { initial: Banner[] }) {
  const [items, setItems] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({
    imageUrl: "",
    overlayText: "",
    sortOrder: "0",
    active: true,
  });

  useEffect(() => {
    setItems(initial);
  }, [initial]);

  async function uploadFile(file: File) {
    const body = new FormData();
    body.append("file", file);
    const headers: HeadersInit = {};
    const csrf = getCsrfToken();
    if (csrf) headers["X-XSRF-TOKEN"] = csrf;
    const response = await fetch("/api/shop/admin/uploads", {
      method: "POST",
      headers,
      credentials: "include",
      body,
    });
    if (!response.ok) throw new Error("업로드 실패");
    const data = (await response.json()) as { url: string };
    return data.url;
  }

  async function createBanner() {
    if (!form.imageUrl) {
      setError("이미지를 업로드해 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    const headers: HeadersInit = { "Content-Type": "application/json" };
    const csrf = getCsrfToken();
    if (csrf) headers["X-XSRF-TOKEN"] = csrf;
    try {
      const response = await fetch("/api/shop/admin/hero-banners", {
        method: "POST",
        headers,
        credentials: "include",
        body: JSON.stringify({
          imageUrl: form.imageUrl,
          overlayText: form.overlayText || null,
          sortOrder: Number(form.sortOrder) || 0,
          active: form.active,
        }),
      });
      if (!response.ok) {
        const data = (await response.json().catch(() => null)) as { message?: string } | null;
        setError(data?.message ?? "등록 실패");
        return;
      }
      const created = (await response.json()) as Banner;
      setItems((prev) => [...prev, created].sort((a, b) => a.sortOrder - b.sortOrder));
      setForm({ imageUrl: "", overlayText: "", sortOrder: "0", active: true });
    } finally {
      setBusy(false);
    }
  }

  async function toggleActive(banner: Banner) {
    const headers: HeadersInit = { "Content-Type": "application/json" };
    const csrf = getCsrfToken();
    if (csrf) headers["X-XSRF-TOKEN"] = csrf;
    const response = await fetch(`/api/shop/admin/hero-banners/${banner.bannerId}`, {
      method: "PUT",
      headers,
      credentials: "include",
      body: JSON.stringify({
        imageUrl: banner.imageUrl,
        overlayText: banner.overlayText,
        sortOrder: banner.sortOrder,
        active: !banner.active,
      }),
    });
    if (!response.ok) {
      setError("사용여부 변경 실패");
      return;
    }
    const updated = (await response.json()) as Banner;
    setItems((prev) => prev.map((item) => (item.bannerId === updated.bannerId ? updated : item)));
  }

  async function removeBanner(bannerId: number) {
    const headers: HeadersInit = {};
    const csrf = getCsrfToken();
    if (csrf) headers["X-XSRF-TOKEN"] = csrf;
    const response = await fetch(`/api/shop/admin/hero-banners/${bannerId}`, {
      method: "DELETE",
      headers,
      credentials: "include",
    });
    if (!response.ok && response.status !== 204) {
      setError("삭제 실패");
      return;
    }
    setItems((prev) => prev.filter((item) => item.bannerId !== bannerId));
  }

  return (
    <div className="flex flex-col gap-8">
      <section className="rounded-xl border border-border bg-surface p-5">
        <h2 className="mb-4 text-lg font-semibold">배너 등록</h2>
        <div className="flex flex-col gap-3">
          <label className="inline-flex w-fit cursor-pointer items-center rounded-xl border border-border px-3 py-2 text-sm hover:bg-surface-soft">
            이미지 업로드
            <input
              type="file"
              accept="image/*"
              className="hidden"
              onChange={async (e) => {
                const file = e.target.files?.[0];
                e.target.value = "";
                if (!file) return;
                try {
                  const url = await uploadFile(file);
                  setForm((prev) => ({ ...prev, imageUrl: url }));
                } catch {
                  setError("업로드에 실패했습니다.");
                }
              }}
            />
          </label>
          {form.imageUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={resolveMediaUrl(form.imageUrl)}
              alt="preview"
              className="aspect-video max-w-md rounded-xl object-cover"
            />
          ) : null}
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium">중앙 텍스트</span>
            <input
              className="h-11 rounded-xl border border-border bg-background px-3"
              value={form.overlayText}
              onChange={(e) => setForm((prev) => ({ ...prev, overlayText: e.target.value }))}
              placeholder="이미지 정중앙에 표시할 문구"
            />
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium">정렬 순서</span>
            <input
              className="h-11 rounded-xl border border-border bg-background px-3"
              value={form.sortOrder}
              onChange={(e) => setForm((prev) => ({ ...prev, sortOrder: e.target.value }))}
            />
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={form.active}
              onChange={(e) => setForm((prev) => ({ ...prev, active: e.target.checked }))}
            />
            사용
          </label>
          {error ? <p className="text-sm text-danger">{error}</p> : null}
          <Button type="button" disabled={busy} onClick={() => void createBanner()}>
            {busy ? "등록 중..." : "배너 등록"}
          </Button>
        </div>
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="text-lg font-semibold">등록된 배너</h2>
        {items.length === 0 ? (
          <p className="text-sm text-muted-foreground">등록된 배너가 없습니다.</p>
        ) : (
          <ul className="grid gap-4 md:grid-cols-2">
            {items.map((banner) => (
              <li key={banner.bannerId} className="rounded-xl border border-border bg-surface p-4">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={resolveMediaUrl(banner.imageUrl)}
                  alt={banner.overlayText ?? "banner"}
                  className="aspect-video w-full rounded-lg object-cover"
                />
                <p className="mt-2 text-sm font-medium">{banner.overlayText || "(텍스트 없음)"}</p>
                <p className="text-xs text-muted-foreground">
                  순서 {banner.sortOrder} · {banner.active ? "사용" : "미사용"}
                </p>
                <div className="mt-3 flex gap-2">
                  <button
                    type="button"
                    className="rounded-lg border border-border px-3 py-1.5 text-xs"
                    onClick={() => void toggleActive(banner)}
                  >
                    {banner.active ? "미사용으로" : "사용으로"}
                  </button>
                  <button
                    type="button"
                    className="rounded-lg border border-border px-3 py-1.5 text-xs text-danger"
                    onClick={() => void removeBanner(banner.bannerId)}
                  >
                    삭제
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
