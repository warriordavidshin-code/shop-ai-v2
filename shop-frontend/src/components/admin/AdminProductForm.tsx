"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";
import { RichTextEditor } from "@/components/admin/RichTextEditor";
import { AiProductPanel, type AiApply } from "@/components/admin/ops/AiProductPanel";
import { PriceRecommendationView } from "@/components/admin/ops/PricingClient";
import { resolveMediaUrl } from "@/lib/media";
import { discountRateFrom, salePriceFromRate, validatePrices } from "@/features/admin/pricing";
import { buildSkuCode } from "@/features/admin/products";
import {
  errorMessage,
  matchItems,
  postForm,
  type PriceRecommendation,
  type ProductPrepare,
  recommendPrice,
  won,
} from "@/features/admin/ops";

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

type CategoryNode = { categoryId: number; name: string; children: CategoryNode[] };

type ProductInitial = {
  productName: string;
  brandName: string;
  categoryId: number;
  summary: string;
  description: string;
  normalPrice: number;
  salePrice: number;
  status: string;
  images?: ImageDraft[];
  fitType?: string | null;
  material?: string | null;
  thickness?: string | null;
  stretch?: string | null;
  seeThrough?: string | null;
  season?: string | null;
  searchKeywords?: string | null;
  seoDescription?: string | null;
  purchasePrice?: number | null;
};

const ATTRIBUTE_FIELDS = [
  ["material", "소재", "예: 면 100% (실제 라벨 확인 후 입력)"],
  ["season", "시즌", "예: 봄/가을"],
  ["fitType", "핏", "예: 루즈핏"],
  ["thickness", "두께감", "예: 보통"],
  ["stretch", "신축성", "예: 약간 있음"],
  ["seeThrough", "비침", "예: 없음"],
] as const;

function flattenCategories(nodes: CategoryNode[], depth = 0): { id: number; label: string }[] {
  return nodes.flatMap((node) => [
    { id: node.categoryId, label: `${"— ".repeat(depth)}${node.name}` },
    ...flattenCategories(node.children ?? [], depth + 1),
  ]);
}

export function AdminProductForm({
  mode,
  productId,
  initial,
  ledgerItemId,
}: {
  mode: "create" | "edit";
  productId?: number;
  initial?: ProductInitial;
  ledgerItemId?: number;
}) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [categories, setCategories] = useState<{ id: number; label: string }[]>([]);
  const [form, setForm] = useState({
    productName: initial?.productName ?? "",
    brandName: initial?.brandName ?? "BoutiqueCamel",
    categoryId: String(initial?.categoryId ?? 3),
    summary: initial?.summary ?? "",
    description: initial?.description ?? "",
    normalPrice: String(initial?.normalPrice ?? 59000),
    salePrice: String(initial?.salePrice ?? 49000),
    discountRate: String(discountRateFrom(initial?.normalPrice ?? 59000, initial?.salePrice ?? 49000) ?? 0),
    status: initial?.status ?? (ledgerItemId ? "DRAFT" : "ON_SALE"),
    fitType: initial?.fitType ?? "",
    material: initial?.material ?? "",
    thickness: initial?.thickness ?? "",
    stretch: initial?.stretch ?? "",
    seeThrough: initial?.seeThrough ?? "",
    season: initial?.season ?? "",
    searchKeywords: initial?.searchKeywords ?? "",
    seoDescription: initial?.seoDescription ?? "",
    purchasePrice: initial?.purchasePrice === null || initial?.purchasePrice === undefined ? "" : String(initial.purchasePrice),
  });
  const [images, setImages] = useState<ImageDraft[]>(
    (initial?.images ?? []).map((img, index) => ({
      imageUrl: img.imageUrl,
      imageType: img.imageType === "MAIN" ? "MAIN" : "DETAIL",
      altText: img.altText ?? "",
      sortOrder: img.sortOrder ?? index,
    })),
  );
  const [skuDrafts, setSkuDrafts] = useState<{ color: string; size: string }[]>([]);
  const [prepare, setPrepare] = useState<ProductPrepare | null>(null);
  const [confirmations, setConfirmations] = useState<Record<string, boolean>>({});
  const [pricing, setPricing] = useState<PriceRecommendation | null>(null);
  const [aiNotes, setAiNotes] = useState<string[]>([]);

  useEffect(() => {
    fetch("/api/shop/categories", { credentials: "include" })
      .then((r) => (r.ok ? r.json() : []))
      .then((tree: CategoryNode[]) => setCategories(flattenCategories(tree)))
      .catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    if (!ledgerItemId) return;
    const body = new FormData();
    body.append("ledgerItemId", String(ledgerItemId));
    postForm<ProductPrepare>("/admin/products/ai/prepare", body)
      .then((data) => {
        setPrepare(data);
        applyDraft(data);
        setPricing(data.pricing);
        setConfirmations(Object.fromEntries(data.needsConfirmation.map((item) => [item, false])));
      })
      .catch((e) => setError(errorMessage(e, "매입 품목 정보를 불러오지 못했습니다.")));
  }, [ledgerItemId]);

  function applyDraft(data: ProductPrepare) {
    const d = data.draft;
    setForm((prev) => {
      const normal = d.normalPrice ?? Number(prev.normalPrice);
      const sale = d.salePrice ?? Number(prev.salePrice);
      return {
        ...prev,
        productName: d.productName || prev.productName,
        summary: d.summary ?? prev.summary,
        description: d.description ?? prev.description,
        categoryId: d.categoryId ? String(d.categoryId) : prev.categoryId,
        material: d.material ?? prev.material,
        season: d.season ?? prev.season,
        fitType: d.fitType ?? prev.fitType,
        thickness: d.thickness ?? prev.thickness,
        stretch: d.stretch ?? prev.stretch,
        seeThrough: d.seeThrough ?? prev.seeThrough,
        searchKeywords: d.searchKeywords ?? prev.searchKeywords,
        seoDescription: d.seoDescription ?? prev.seoDescription,
        purchasePrice: d.purchasePrice === null ? prev.purchasePrice : String(d.purchasePrice),
        normalPrice: String(normal),
        salePrice: String(sale),
        discountRate: String(discountRateFrom(normal, sale) ?? 0),
      };
    });
    setSkuDrafts(d.skus ?? []);
  }

  function applyAi(apply: AiApply) {
    setForm((prev) => ({ ...prev, ...apply.fields }));
    if (apply.altTexts) {
      setImages((prev) =>
        prev.map((img) => {
          const hit = apply.altTexts?.find((a) => a.imageUrl === img.imageUrl);
          return hit ? { ...img, altText: hit.altText } : img;
        }),
      );
    }
    setAiNotes(apply.notes);
  }

  function onNormalPriceChange(normalPrice: string) {
    setForm((prev) => {
      const sale = salePriceFromRate(normalPrice, prev.discountRate);
      return { ...prev, normalPrice, salePrice: sale === null ? prev.salePrice : String(sale) };
    });
  }

  function onDiscountRateChange(discountRate: string) {
    setForm((prev) => {
      const sale = salePriceFromRate(prev.normalPrice, discountRate);
      return { ...prev, discountRate, salePrice: sale === null ? prev.salePrice : String(sale) };
    });
  }

  function onSalePriceChange(salePrice: string) {
    setForm((prev) => {
      const rate = discountRateFrom(prev.normalPrice, salePrice);
      return { ...prev, salePrice, discountRate: rate === null ? prev.discountRate : String(rate) };
    });
  }

  function pickPrice(price: number) {
    setForm((prev) => {
      const normal = Math.max(Number(prev.normalPrice) || 0, price);
      return { ...prev, normalPrice: String(normal), salePrice: String(price), discountRate: String(discountRateFrom(normal, price) ?? 0) };
    });
  }

  async function recalcPrice() {
    setError(null);
    try {
      const cost = Number(form.purchasePrice);
      setPricing(
        await recommendPrice({
          unitCost: form.purchasePrice !== "" && Number.isFinite(cost) ? cost : null,
          productId: productId ?? null,
          currentSalePrice: Number(form.salePrice) || null,
        }),
      );
    } catch (e) {
      setError(errorMessage(e, "가격을 계산하지 못했습니다."));
    }
  }

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
    const priceError = validatePrices(form.normalPrice, form.salePrice);
    if (priceError) {
      setError(priceError);
      setBusy(false);
      return;
    }
    if (images.length > 0 && images.filter((img) => img.imageType === "MAIN").length !== 1) {
      setError("대표 이미지를 1개 선택해 주세요.");
      setBusy(false);
      return;
    }
    const unchecked = Object.entries(confirmations).filter(([, ok]) => !ok).map(([item]) => item);
    if (unchecked.length > 0) {
      setError(`등록 전에 확인해 주세요: ${unchecked.join(", ")}`);
      setBusy(false);
      return;
    }
    const text = (value: string) => (value.trim() === "" ? null : value.trim());
    const stamp = `N${Date.now().toString(36).toUpperCase()}`;
    const payload = {
      productName: form.productName,
      brandName: form.brandName,
      categoryId: Number(form.categoryId),
      summary: form.summary,
      description: form.description,
      normalPrice: Number(form.normalPrice),
      salePrice: Number(form.salePrice),
      status: form.status,
      fitType: text(form.fitType),
      material: text(form.material),
      thickness: text(form.thickness),
      stretch: text(form.stretch),
      seeThrough: text(form.seeThrough),
      season: text(form.season),
      searchKeywords: text(form.searchKeywords),
      seoDescription: text(form.seoDescription),
      purchasePrice: form.purchasePrice === "" ? null : Number(form.purchasePrice),
      images: images.map((img, index) => ({
        imageUrl: img.imageUrl,
        imageType: img.imageType,
        altText: img.altText,
        sortOrder: index,
      })),
      ...(mode === "create" && skuDrafts.length > 0
        ? {
            skus: skuDrafts
              .filter((sku) => sku.color.trim() && sku.size.trim())
              .map((sku) => ({
                skuCode: buildSkuCode(0, sku.color, sku.size).replace(/^P0/, stamp),
                color: sku.color.trim(),
                size: sku.size.trim(),
                stockQuantity: 0,
              })),
          }
        : {}),
    };
    const path = mode === "create" ? "/api/shop/admin/products" : `/api/shop/admin/products/${productId}`;
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
      if (prepare && ledgerItemId) {
        const created = (await response.json()) as {
          productId: number;
          skus: { skuId: number; color: string; size: string }[];
        };
        const sku =
          created.skus.find((s) => s.color === (prepare.purchase.color ?? "") && s.size === (prepare.purchase.size ?? "")) ??
          (created.skus.length === 1 ? created.skus[0] : undefined);
        if (sku) {
          await matchItems({ itemId: ledgerItemId, action: "CONFIRM", productId: created.productId, skuId: sku.skuId, remember: true });
          router.push(`/admin/purchases/${prepare.purchase.ledgerId}`);
        } else {
          router.push(`/admin/purchases/matching?ledgerId=${prepare.purchase.ledgerId}`);
        }
        router.refresh();
        return;
      }
      router.push("/admin/products");
      router.refresh();
    } catch (err) {
      setError(errorMessage(err, "저장에 실패했습니다."));
    } finally {
      setBusy(false);
    }
  }

  const inputCls = "h-11 rounded-xl border border-border bg-surface px-3";

  return (
    <form onSubmit={onSubmit} className="flex max-w-3xl flex-col gap-5">
      {prepare ? (
        <div className="flex flex-col gap-2 rounded-xl border border-brand/30 bg-surface-soft p-4 text-sm">
          <p className="font-semibold">매입 품목에서 신규 상품 등록</p>
          <p>
            {prepare.purchase.managementNo} · {prepare.purchase.supplierName ?? "-"} · {prepare.purchase.productName}{" "}
            {[prepare.purchase.color, prepare.purchase.size].filter(Boolean).join("/")} · {prepare.purchase.quantity ?? "-"}개 × {won(prepare.purchase.unitCost)}
          </p>
          {prepare.warnings.map((w, i) => (
            <p key={i} className="text-amber-800">
              {w}
            </p>
          ))}
          {prepare.needsConfirmation.length > 0 ? (
            <div className="flex flex-col gap-1">
              <p className="font-medium">등록 전에 관리자가 직접 확인해야 하는 항목</p>
              {prepare.needsConfirmation.map((item) => (
                <label key={item} className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={confirmations[item] ?? false}
                    onChange={(e) => setConfirmations((prev) => ({ ...prev, [item]: e.target.checked }))}
                  />
                  {item}
                </label>
              ))}
            </div>
          ) : null}
        </div>
      ) : null}

      <AiProductPanel imageUrls={images.map((img) => img.imageUrl)} productName={form.productName} onApply={applyAi} />
      {aiNotes.length > 0 ? (
        <ul className="list-disc rounded-lg border border-amber-300 bg-amber-50 py-2 pl-7 pr-3 text-xs text-amber-900">
          {aiNotes.map((note, i) => (
            <li key={i}>{note}</li>
          ))}
        </ul>
      ) : null}

      {(
        [
          ["productName", "상품명"],
          ["brandName", "브랜드"],
          ["summary", "요약"],
        ] as const
      ).map(([key, label]) => (
        <label key={key} className="flex flex-col gap-1.5 text-sm">
          <span className="font-medium">{label}</span>
          <input
            className={inputCls}
            value={form[key]}
            onChange={(e) => setForm((prev) => ({ ...prev, [key]: e.target.value }))}
            required={key === "productName" || key === "brandName"}
          />
        </label>
      ))}

      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">카테고리</span>
        {categories.length > 0 ? (
          <select className={inputCls} value={form.categoryId} onChange={(e) => setForm((prev) => ({ ...prev, categoryId: e.target.value }))}>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.label}
              </option>
            ))}
          </select>
        ) : (
          <input className={inputCls} value={form.categoryId} onChange={(e) => setForm((prev) => ({ ...prev, categoryId: e.target.value }))} />
        )}
      </label>

      <div className="grid gap-3 sm:grid-cols-3">
        {ATTRIBUTE_FIELDS.map(([key, label, placeholder]) => (
          <label key={key} className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium">{label}</span>
            <input className={inputCls} placeholder={placeholder} value={form[key]} onChange={(e) => setForm((prev) => ({ ...prev, [key]: e.target.value }))} />
          </label>
        ))}
      </div>

      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">검색 키워드 (쉼표로 구분)</span>
        <input className={inputCls} maxLength={500} value={form.searchKeywords} onChange={(e) => setForm((prev) => ({ ...prev, searchKeywords: e.target.value }))} />
      </label>
      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">SEO 설명 (최대 300자)</span>
        <textarea
          className="min-h-20 rounded-xl border border-border bg-surface p-3"
          maxLength={300}
          value={form.seoDescription}
          onChange={(e) => setForm((prev) => ({ ...prev, seoDescription: e.target.value }))}
        />
      </label>

      <div className="flex flex-col gap-2 rounded-xl border border-border p-3">
        <div className="grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end">
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium">매입가 / 표준원가 (원, 관리자 전용)</span>
            <input
              type="number"
              min={0}
              step={1}
              inputMode="numeric"
              className={inputCls}
              value={form.purchasePrice}
              onChange={(e) => setForm((prev) => ({ ...prev, purchasePrice: e.target.value }))}
            />
          </label>
          <button type="button" className="h-11 rounded-xl border border-border px-4 text-sm hover:bg-surface-soft" onClick={() => void recalcPrice()}>
            가격 재계산
          </button>
        </div>
        {pricing ? <PriceRecommendationView rec={pricing} onPick={pickPrice} /> : (
          <p className="text-xs text-muted-foreground">매입가를 입력하고 &lsquo;가격 재계산&rsquo;을 누르면 가격 정책에 따른 추천 판매가와 마진을 보여줍니다.</p>
        )}
      </div>

      <div className="flex flex-col gap-2">
        <div className="grid gap-3 sm:grid-cols-[1fr_8rem_1fr]">
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium">정상가 (원)</span>
            <input
              type="number"
              min={0}
              step={1}
              inputMode="numeric"
              className={inputCls}
              value={form.normalPrice}
              onChange={(e) => onNormalPriceChange(e.target.value)}
              required
            />
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium">할인율 (%)</span>
            <input
              type="number"
              min={0}
              max={100}
              step={1}
              inputMode="numeric"
              className={inputCls}
              value={form.discountRate}
              onChange={(e) => onDiscountRateChange(e.target.value)}
            />
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium">판매가 (원)</span>
            <input
              type="number"
              min={0}
              step={1}
              inputMode="numeric"
              className={inputCls}
              value={form.salePrice}
              onChange={(e) => onSalePriceChange(e.target.value)}
              required
            />
          </label>
        </div>
        <p className="text-xs text-muted-foreground">
          정상가나 할인율을 바꾸면 판매가가 자동 계산됩니다. 판매가를 직접 수정하면 할인율이 다시 계산됩니다.
        </p>
      </div>

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
            이미지를 추가하면 미리보기가 표시되고, AI 자동생성에 사용할 수 있습니다.
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
                <input
                  className="mt-2 h-8 w-full rounded border border-border bg-surface px-2 text-xs"
                  placeholder="대체 텍스트(alt)"
                  value={img.altText}
                  onChange={(e) => setImages((prev) => prev.map((it, i) => (i === index ? { ...it, altText: e.target.value } : it)))}
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

      {mode === "create" ? (
        <div className="flex flex-col gap-2 rounded-xl border border-border p-3 text-sm">
          <div className="flex items-center justify-between">
            <span className="font-medium">옵션 (색상 / 사이즈) — 재고 0으로 생성</span>
            <button type="button" className="rounded-lg border border-border px-2 py-1 text-xs" onClick={() => setSkuDrafts((prev) => [...prev, { color: "", size: "" }])}>
              옵션 추가
            </button>
          </div>
          {skuDrafts.length === 0 ? (
            <p className="text-xs text-muted-foreground">옵션은 저장 후 재고 관리 화면에서도 추가할 수 있습니다.</p>
          ) : (
            skuDrafts.map((sku, index) => (
              <div key={index} className="flex gap-2">
                <input className="h-9 flex-1 rounded border border-border bg-surface px-2" placeholder="색상" value={sku.color} onChange={(e) => setSkuDrafts((prev) => prev.map((s, i) => (i === index ? { ...s, color: e.target.value } : s)))} />
                <input className="h-9 flex-1 rounded border border-border bg-surface px-2" placeholder="사이즈" value={sku.size} onChange={(e) => setSkuDrafts((prev) => prev.map((s, i) => (i === index ? { ...s, size: e.target.value } : s)))} />
                <button type="button" className="text-xs text-danger underline" onClick={() => setSkuDrafts((prev) => prev.filter((_, i) => i !== index))}>
                  삭제
                </button>
              </div>
            ))
          )}
          <p className="text-xs text-muted-foreground">재고는 매입 후 &lsquo;입고 확정&rsquo; 시에만 늘어납니다.</p>
        </div>
      ) : null}

      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">상태</span>
        <select
          className={inputCls}
          value={form.status}
          onChange={(e) => setForm((prev) => ({ ...prev, status: e.target.value }))}
        >
          <option value="DRAFT">DRAFT (비공개 임시저장)</option>
          <option value="ON_SALE">ON_SALE</option>
          <option value="SOLD_OUT">SOLD_OUT</option>
          <option value="HIDDEN">HIDDEN</option>
          <option value="DISCONTINUED">DISCONTINUED</option>
        </select>
      </label>
      {error ? <p className="text-sm text-danger">{error}</p> : null}
      <Button type="submit" disabled={busy || uploading}>
        {busy ? "저장 중..." : prepare ? "상품 등록 후 매입 품목에 연결" : "저장"}
      </Button>
    </form>
  );
}