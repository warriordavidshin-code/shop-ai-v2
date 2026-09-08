"use client";

import { useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/Button";
import { ProductGrid } from "@/components/product/ProductCard";
import { parseApiError, shopFetch } from "@/lib/api/client";
import { productSummarySchema, type ProductSummary } from "@/features/products/api";
import { z } from "zod";

const occasions = ["출근", "데이트", "결혼식", "여행", "데일리"] as const;
const styles = ["미니멀", "여성스러움", "세련됨", "편안함", "로맨틱"] as const;

export function StyleClient() {
  const [step, setStep] = useState(0);
  const [occasion, setOccasion] = useState<string>("데일리");
  const [style, setStyle] = useState<string>("편안함");
  const [color, setColor] = useState("Ivory");
  const [budget, setBudget] = useState("100000");
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function recommend() {
    setBusy(true);
    setMessage(null);
    try {
      const response = await shopFetch("/ai/outfit-recommendations", {
        method: "POST",
        body: JSON.stringify({
          occasion,
          style,
          colors: [color],
          budget: Number(budget),
        }),
      });
      if (!response.ok) throw await parseApiError(response);
      const data = await response.json();
      const outfits = z
        .object({
          outfits: z.array(z.object({ items: z.array(productSummarySchema).optional() })).optional(),
          items: z.array(productSummarySchema).optional(),
          provider: z.string().optional(),
        })
        .passthrough()
        .parse(data);
      const flat =
        outfits.outfits?.flatMap((o) => o.items ?? []) ??
        outfits.items ??
        [];
      setProducts(flat);
      setMessage(`추천 완료 (provider: ${outfits.provider ?? "rule"})`);
      setStep(4);
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "추천 실패");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      {step === 0 ? (
        <Step title="상황" options={occasions} value={occasion} onChange={setOccasion} onNext={() => setStep(1)} />
      ) : null}
      {step === 1 ? (
        <Step title="스타일" options={styles} value={style} onChange={setStyle} onNext={() => setStep(2)} />
      ) : null}
      {step === 2 ? (
        <div className="flex flex-col gap-3">
          <h2 className="text-lg font-semibold">선호 색상</h2>
          <input
            className="h-11 rounded-xl border border-border px-3"
            value={color}
            onChange={(e) => setColor(e.target.value)}
          />
          <Button onClick={() => setStep(3)}>다음</Button>
        </div>
      ) : null}
      {step === 3 ? (
        <div className="flex flex-col gap-3">
          <h2 className="text-lg font-semibold">예산</h2>
          <input
            type="number"
            className="h-11 rounded-xl border border-border px-3"
            value={budget}
            onChange={(e) => setBudget(e.target.value)}
          />
          <Button disabled={busy} onClick={recommend}>
            {busy ? "추천 중..." : "코디 추천받기"}
          </Button>
        </div>
      ) : null}
      {step === 4 ? (
        <div className="flex flex-col gap-4">
          <h2 className="text-lg font-semibold">추천 코디</h2>
          {message ? <p className="text-sm text-muted-foreground">{message}</p> : null}
          <ProductGrid products={products} />
          <Link href="/products" className="text-sm text-brand hover:underline">
            상품 더 보기
          </Link>
        </div>
      ) : null}
    </div>
  );
}

function Step({
  title,
  options,
  value,
  onChange,
  onNext,
}: {
  title: string;
  options: readonly string[];
  value: string;
  onChange: (v: string) => void;
  onNext: () => void;
}) {
  return (
    <div className="flex flex-col gap-3">
      <h2 className="text-lg font-semibold">{title}</h2>
      <div className="flex flex-wrap gap-2">
        {options.map((opt) => (
          <button
            key={opt}
            type="button"
            onClick={() => onChange(opt)}
            className={`h-11 rounded-xl border px-4 text-sm ${
              value === opt ? "border-brand bg-brand-soft" : "border-border bg-surface"
            }`}
          >
            {opt}
          </button>
        ))}
      </div>
      <Button onClick={onNext}>다음</Button>
    </div>
  );
}
