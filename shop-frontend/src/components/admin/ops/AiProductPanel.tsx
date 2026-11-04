"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  adminJson,
  type AiStatus,
  BILLING_LABELS,
  errorMessage,
  isPaidApprovalRequired,
  postForm,
  type ProductAnalysis,
  PROVIDER_LABELS,
} from "@/features/admin/ops";

export type AiApply = {
  fields: Record<string, string>;
  altTexts?: { imageUrl: string; altText: string }[];
  notes: string[];
};

function escapeHtml(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

/** Plain AI text becomes simple paragraphs; it is never inserted as raw HTML. */
export function textToHtml(text: string): string {
  return text
    .split(/\n{2,}/)
    .map((block) => `<p>${escapeHtml(block.trim()).replace(/\n/g, "<br>")}</p>`)
    .join("");
}

type Row = { key: string; label: string; value: string; note?: string };

export function AiProductPanel({
  imageUrls,
  productName,
  onApply,
}: {
  imageUrls: string[];
  productName: string;
  onApply: (apply: AiApply) => void;
}) {
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [open, setOpen] = useState(false);
  const [hint, setHint] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ProductAnalysis | null>(null);
  const [nameIndex, setNameIndex] = useState(0);
  const [selected, setSelected] = useState<Record<string, boolean>>({});
  const [paidPrompt, setPaidPrompt] = useState<string | null>(null);

  useEffect(() => {
    adminJson<AiStatus>("/admin/ai/analysis-status").then(setStatus).catch(() => setStatus(null));
  }, []);

  const usable = status?.productEnabled && status.available;

  async function analyze(forceRefresh: boolean, allowPaid = false) {
    if (imageUrls.length === 0) {
      setError("먼저 상품 이미지를 추가해 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    setPaidPrompt(null);
    const body = new FormData();
    imageUrls.forEach((url) => body.append("imageUrls", url));
    const h = hint.trim() || productName.trim();
    if (h) body.append("hint", h);
    if (forceRefresh) body.append("forceRefresh", "true");
    if (allowPaid) body.append("allowPaid", "true");
    try {
      const data = await postForm<ProductAnalysis>("/admin/products/ai/analyze", body);
      setResult(data);
      setNameIndex(0);
      setSelected({});
    } catch (e) {
      if (isPaidApprovalRequired(e)) {
        setPaidPrompt(errorMessage(e));
      } else {
        setError(errorMessage(e, "AI 분석에 실패했습니다. 직접 입력해 주세요."));
      }
    } finally {
      setBusy(false);
    }
  }

  function approvePaid() {
    if (window.confirm("유료 AI(OpenAI)를 1회 호출합니다. 비용이 발생할 수 있으며 월 예산 한도 안에서만 실행됩니다. 진행할까요?")) {
      void analyze(true, true);
    }
  }

  const rows: Row[] = result
    ? ([
        { key: "productName", label: "상품명", value: result.productNameCandidates[nameIndex] ?? "" },
        { key: "categoryId", label: "카테고리", value: result.categoryId ? String(result.categoryId) : "", note: [result.categoryName, result.categoryReason].filter(Boolean).join(" — ") },
        { key: "summary", label: "요약", value: result.summary ?? "" },
        { key: "description", label: "상세 설명", value: result.description ?? "" },
        { key: "material", label: "소재 (이미지 기반 추정)", value: result.materialEstimate ?? "", note: "라벨로 반드시 확인하세요." },
        { key: "season", label: "시즌", value: result.season ?? "" },
        { key: "fitType", label: "핏", value: result.fitType ?? "" },
        { key: "thickness", label: "두께감", value: result.thickness ?? "" },
        { key: "stretch", label: "신축성", value: result.stretch ?? "" },
        { key: "seeThrough", label: "비침", value: result.seeThrough ?? "" },
        { key: "searchKeywords", label: "검색 키워드", value: result.keywords.join(", ") },
        { key: "seoDescription", label: "SEO 설명", value: result.seoDescription ?? "" },
      ] satisfies Row[]).filter((row) => row.value !== "")
    : [];

  function apply() {
    if (!result) return;
    const fields: Record<string, string> = {};
    for (const row of rows) {
      if (selected[row.key] === false) continue;
      fields[row.key] = row.key === "description" ? textToHtml(row.value) : row.value;
    }
    const notes = [
      ...(result.mock ? ["[MOCK] 테스트 결과를 적용했습니다. 실제 AI 분석 결과가 아닙니다."] : []),
      ...result.needsConfirmation.map((item) => `확인 필요: ${item}`),
      ...result.warnings,
      ...(result.colors.length ? [`이미지에서 보이는 색상: ${result.colors.join(", ")} (옵션은 직접 확인)`] : []),
      ...(result.style ? [`스타일: ${result.style}`] : []),
    ];
    onApply({
      fields,
      altTexts: selected.altTexts === false ? undefined : result.altTexts.filter((a) => a.imageUrl).map((a) => ({ imageUrl: a.imageUrl as string, altText: a.altText })),
      notes,
    });
    setOpen(false);
  }

  return (
    <div className="rounded-xl border border-border bg-surface p-3 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="font-semibold">AI 상품정보 자동생성</p>
          <p className="text-xs text-muted-foreground">
            업로드한 상품 이미지로 상품명·카테고리·설명·키워드·SEO·대체텍스트 초안을 만듭니다. 적용해도 자동으로 게시되지 않습니다.
          </p>
        </div>
        <button type="button" className="rounded-lg border border-border px-3 py-2 text-sm hover:bg-surface-soft" onClick={() => setOpen((v) => !v)}>
          {open ? "닫기" : "AI 설명 생성"}
        </button>
      </div>
      {open ? (
        <div className="mt-3 flex flex-col gap-3">
          {status && !usable ? (
            <p className="rounded border border-amber-300 bg-amber-50 px-2 py-1 text-amber-900">
              AI 상품 분석을 사용할 수 없습니다
              {!status.productEnabled ? " (AI_PRODUCT_ENABLED=false)" : ""}
              {status.mode === "MANUAL" || status.mode === "LOCAL_ONLY" ? " (현재 AI 모드에서는 외부 AI를 호출하지 않습니다)" : ""}
              {status.geminiKeyConfigured === false && !status.apiKeyConfigured ? " (서버에 GEMINI_API_KEY 미설정)" : ""}. 직접 입력해 주세요.
            </p>
          ) : null}
          {status?.mockMode ? (
            <p className="rounded border border-amber-300 bg-amber-50 px-2 py-1 text-amber-900">Mock 모드입니다. 결과는 [MOCK] 테스트 데이터입니다.</p>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <input
              className="h-10 min-w-[16rem] flex-1 rounded-lg border border-border bg-surface px-3"
              placeholder="참고 메모 (선택, 예: 여름 린넨 셔츠)"
              value={hint}
              maxLength={300}
              onChange={(e) => setHint(e.target.value)}
            />
            <button type="button" className="h-10 rounded-lg bg-brand px-4 text-white disabled:opacity-50" disabled={busy || !usable || imageUrls.length === 0} onClick={() => void analyze(false)}>
              {busy ? "분석 중..." : `이미지 ${imageUrls.length}장 분석`}
            </button>
            {result ? (
              <button type="button" className="h-10 rounded-lg border border-border px-3" disabled={busy} onClick={() => void analyze(true)}>
                다시 생성
              </button>
            ) : null}
          </div>
          {error ? <p className="text-danger">{error}</p> : null}
          {paidPrompt ? (
            <div className="rounded border border-amber-300 bg-amber-50 px-2 py-2 text-amber-900">
              <p>{paidPrompt}</p>
              <button type="button" className="mt-2 h-9 rounded-lg bg-brand px-3 text-white disabled:opacity-50" disabled={busy} onClick={approvePaid}>
                유료 AI(OpenAI) 1회 사용 승인
              </button>
            </div>
          ) : null}
          {result ? (
            <div className="flex flex-col gap-2">
              <p className="text-xs text-muted-foreground">
                {result.mock ? "MOCK" : `${PROVIDER_LABELS[result.provider] ?? result.provider} ${result.model ?? ""}`}
                {result.billingType ? ` · ${BILLING_LABELS[result.billingType] ?? result.billingType}` : ""} {result.cached ? "· 캐시" : ""}{" "}
                {result.confidence !== null ? `· 신뢰도 ${Math.round(Number(result.confidence) * 100)}%` : ""}
              </p>
              {result.notices && result.notices.length > 0 ? (
                <ul className="list-disc pl-4 text-xs text-muted-foreground">
                  {result.notices.map((n, i) => (
                    <li key={i}>{n}</li>
                  ))}
                </ul>
              ) : null}
              {result.similarProducts.length > 0 ? (
                <div className="rounded border border-amber-300 bg-amber-50 px-2 py-1 text-xs text-amber-900">
                  비슷한 기존 상품:{" "}
                  {result.similarProducts.map((p) => (
                    <Link key={p.productId} href={`/admin/products/${p.productId}/edit`} target="_blank" className="mr-2 underline">
                      {p.productName} ({Math.round(Number(p.score) * 100)}%)
                    </Link>
                  ))}
                  — 중복 등록인지 확인하세요.
                </div>
              ) : null}
              {result.productNameCandidates.length > 1 ? (
                <div className="flex flex-wrap gap-1">
                  {result.productNameCandidates.map((name, i) => (
                    <button key={name + i} type="button" className={`rounded px-2 py-1 text-xs ${i === nameIndex ? "bg-brand text-white" : "border border-border"}`} onClick={() => setNameIndex(i)}>
                      {name}
                    </button>
                  ))}
                </div>
              ) : null}
              <ul className="flex flex-col gap-1">
                {rows.map((row) => (
                  <li key={row.key} className="grid grid-cols-[1.5rem_8rem_1fr] gap-2">
                    <input type="checkbox" checked={selected[row.key] !== false} onChange={(e) => setSelected((prev) => ({ ...prev, [row.key]: e.target.checked }))} />
                    <span className="text-muted-foreground">{row.label}</span>
                    <span className="whitespace-pre-line">
                      {row.key === "categoryId" ? row.note || row.value : row.value}
                      {row.note && row.key !== "categoryId" ? <span className="ml-1 text-xs text-amber-700">({row.note})</span> : null}
                    </span>
                  </li>
                ))}
                {result.altTexts.length > 0 ? (
                  <li className="grid grid-cols-[1.5rem_8rem_1fr] gap-2">
                    <input type="checkbox" checked={selected.altTexts !== false} onChange={(e) => setSelected((prev) => ({ ...prev, altTexts: e.target.checked }))} />
                    <span className="text-muted-foreground">대체 텍스트</span>
                    <span>{result.altTexts.map((a) => a.altText).join(" / ")}</span>
                  </li>
                ) : null}
              </ul>
              {result.needsConfirmation.length > 0 ? (
                <p className="text-xs text-amber-800">확인 필요: {result.needsConfirmation.join(", ")}</p>
              ) : null}
              <div>
                <button type="button" className="h-10 rounded-lg bg-brand px-4 text-white" onClick={apply}>
                  선택 항목을 폼에 적용
                </button>
              </div>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
