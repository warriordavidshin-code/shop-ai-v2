"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { RichTextEditor } from "@/components/admin/RichTextEditor";
import { resolveMediaUrl } from "@/lib/media";

function getCsrfToken(): string | undefined {
  if (typeof document === "undefined") return undefined;
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : undefined;
}

type ImageDraft = {
  imageUrl: string;
  imageType: "MAIN" | "DETAIL";
  altText: string;
  sortOrder: number;
};

export function AdminProductForm({
  mode,
  productId,
  initial,
}: {
  mode: "create" | "edit";
  productId?: number;
  initial?: {
    productName: string;
    brandName: string;
    categoryId: number;
    summary: string;
    description: string;
    normalPrice: number;
    salePrice: number;
    status: string;
    images?: ImageDraft[];
  };
}) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [form, setForm] = useState({
    productName: initial?.productName ?? "",
    brandName: initial?.brandName ?? "BoutiqueCamel",
    categoryId: String(initial?.categoryId ?? 3),
    summary: initial?.summary ?? "",
    description: initial?.description ?? "",
    normalPrice: String(initial?.normalPrice ?? 59000),
    salePrice: String(initial?.salePrice ?? 49000),
    status: initial?.status ?? "ON_SALE",
  });
  const [images, setImages] = useState<ImageDraft[]>(
    (initial?.images ?? []).map((img, index) => ({
      imageUrl: img.imageUrl,
      imageType: img.imageType === "MAIN" ? "MAIN" : "DETAIL",
      altText: img.altText ?? "",
      sortOrder: img.sortOrder ?? index,
    })),
  );

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
    if (!response.ok) {
      const data = (await response.json().catch(() => null)) as { message?: string } | null;
      throw new Error(data?.message ?? "업로드 실패");
    }
    const data = (await response.json()) as { url: string };
    return data.url;
  }

  async function onPickFiles(fileList: FileList | null) {
    if (!fileList || fileList.length === 0) return;
    const remaining = 5 - images.length;
    if (remaining <= 0) {
      setError("이미지는 최대 5개까지 등록할 수 있습니다.");
      return;
    }
    setUploading(true);
    setError(null);
    try {
      const files = Array.from(fileList).slice(0, remaining);
      const uploaded: ImageDraft[] = [];
      for (const file of files) {
        const url = await uploadFile(file);
        uploaded.push({
          imageUrl: url,
          imageType: "DETAIL",
          altText: file.name,
          sortOrder: images.length + uploaded.length,
        });
      }
      setImages((prev) => {
        const next = [...prev, ...uploaded];
        if (!next.some((img) => img.imageType === "MAIN") && next.length > 0) {
          next[0] = { ...next[0], imageType: "MAIN" };
        }
        return next;
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "업로드에 실패했습니다.");
    } finally {
      setUploading(false);
    }
  }

  function setMain(index: number) {
    setImages((prev) =>
      prev.map((img, i) => ({
        ...img,
        imageType: i === index ? "MAIN" : "DETAIL",
      })),
    );
  }

  function removeImage(index: number) {
    setImages((prev) => {
      const next = prev.filter((_, i) => i !== index).map((img, i) => ({ ...img, sortOrder: i }));
      if (next.length > 0 && !next.some((img) => img.imageType === "MAIN")) {
        next[0] = { ...next[0], imageType: "MAIN" };
      }
      return next;
    });
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    if (images.length > 0 && images.filter((img) => img.imageType === "MAIN").length !== 1) {
      setError("대표 이미지를 1개 선택해 주세요.");
      setBusy(false);
      return;
    }
    const payload = {
      productName: form.productName,
      brandName: form.brandName,
      categoryId: Number(form.categoryId),
      summary: form.summary,
      description: form.description,
      normalPrice: Number(form.normalPrice),
      salePrice: Number(form.salePrice),
      status: form.status,
      images: images.map((img, index) => ({
        imageUrl: img.imageUrl,
        imageType: img.imageType,
        altText: img.altText,
        sortOrder: index,
      })),
    };
    const path =
      mode === "create" ? "/api/shop/admin/products" : `/api/shop/admin/products/${productId}`;
    const method = mode === "create" ? "POST" : "PUT";
    const headers: HeadersInit = { "Content-Type": "application/json" };
    const csrf = getCsrfToken();
    if (csrf) headers["X-XSRF-TOKEN"] = csrf;

    try {
      const response = await fetch(path, {
        method,
        headers,
        credentials: "include",
        body: JSON.stringify(payload),
      });
      if (!response.ok) {
        const data = (await response.json().catch(() => null)) as { message?: string } | null;
        setError(data?.message ?? "저장에 실패했습니다.");
        return;
      }
      router.push("/admin/products");
      router.refresh();
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="flex max-w-3xl flex-col gap-5">
      {(
        [
          ["productName", "상품명"],
          ["brandName", "브랜드"],
          ["categoryId", "카테고리 ID"],
          ["summary", "요약"],
          ["normalPrice", "정상가"],
          ["salePrice", "판매가"],
        ] as const
      ).map(([key, label]) => (
        <label key={key} className="flex flex-col gap-1.5 text-sm">
          <span className="font-medium">{label}</span>
          <input
            className="h-11 rounded-xl border border-border bg-surface px-3"
            value={form[key]}
            onChange={(e) => setForm((prev) => ({ ...prev, [key]: e.target.value }))}
            required={key === "productName" || key === "brandName"}
          />
        </label>
      ))}

      <div className="flex flex-col gap-2 text-sm">
        <span className="font-medium">상세 설명 (CKEditor)</span>
        <RichTextEditor
          value={form.description}
          onChange={(description) => setForm((prev) => ({ ...prev, description }))}
        />
      </div>

      <div className="flex flex-col gap-3">
        <div className="flex items-center justify-between gap-3">
          <span className="text-sm font-medium">상품 이미지 (최대 5개)</span>
          <label className="inline-flex cursor-pointer items-center rounded-xl border border-border bg-surface px-3 py-2 text-sm hover:bg-surface-soft">
            {uploading ? "업로드 중..." : "이미지 추가"}
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp,image/gif"
              multiple
              className="hidden"
              disabled={uploading || images.length >= 5}
              onChange={(e) => {
                void onPickFiles(e.target.files);
                e.target.value = "";
              }}
            />
          </label>
        </div>
        {images.length === 0 ? (
          <p className="rounded-xl border border-dashed border-border px-4 py-8 text-center text-sm text-muted-foreground">
            이미지를 추가하면 미리보기가 표시됩니다.
          </p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {images.map((img, index) => (
              <li key={`${img.imageUrl}-${index}`} className="rounded-xl border border-border bg-surface p-3">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={resolveMediaUrl(img.imageUrl)}
                  alt={img.altText || `image-${index}`}
                  className="aspect-[4/5] w-full rounded-lg object-cover"
                />
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    className={[
                      "rounded-lg px-2 py-1 text-xs",
                      img.imageType === "MAIN" ? "bg-brand text-white" : "border border-border",
                    ].join(" ")}
                    onClick={() => setMain(index)}
                  >
                    {img.imageType === "MAIN" ? "대표 이미지" : "대표로 지정"}
                  </button>
                  <button
                    type="button"
                    className="rounded-lg border border-border px-2 py-1 text-xs text-danger"
                    onClick={() => removeImage(index)}
                  >
                    삭제
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">상태</span>
        <select
          className="h-11 rounded-xl border border-border bg-surface px-3"
          value={form.status}
          onChange={(e) => setForm((prev) => ({ ...prev, status: e.target.value }))}
        >
          <option value="DRAFT">DRAFT</option>
          <option value="ON_SALE">ON_SALE</option>
          <option value="SOLD_OUT">SOLD_OUT</option>
          <option value="HIDDEN">HIDDEN</option>
          <option value="DISCONTINUED">DISCONTINUED</option>
        </select>
      </label>
      {error ? <p className="text-sm text-danger">{error}</p> : null}
      <Button type="submit" disabled={busy || uploading}>
        {busy ? "저장 중..." : "저장"}
      </Button>
    </form>
  );
}
