"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  adminJson,
  type Candidate,
  errorMessage,
  type LedgerDetail,
  MATCH_STATUS_LABELS,
  matchItems,
  type Page,
  type UnmatchedItem,
  won,
} from "@/features/admin/ops";
import { Badge, buttonClass, Notice, PageHeader, Pager, primaryButtonClass } from "@/components/admin/ops/ui";

function fromDetail(detail: LedgerDetail): UnmatchedItem[] {
  return detail.items
    .filter((item) => item.id !== null && item.matchStatus !== "MATCHED" && item.matchStatus !== "IGNORED")
    .map((item) => ({
      itemId: item.id as number,
      ledgerId: detail.ledger.id,
      managementNo: detail.ledger.managementNo,
      supplierName: detail.ledger.supplierName,
      purchaseDate: detail.ledger.purchaseDate,
      productName: item.productName,
      color: item.color,
      size: item.size,
      supplierProductCode: item.supplierProductCode,
      quantity: item.quantity,
      unitPrice: item.unitPrice,
      matchStatus: item.matchStatus ?? "UNMATCHED",
      matchScore: item.matchScore ?? null,
      matchNote: item.matchNote ?? null,
    }));
}

export function MatchingClient({ ledgerId }: { ledgerId?: number }) {
  const [items, setItems] = useState<UnmatchedItem[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      if (ledgerId) {
        setItems(fromDetail(await adminJson<LedgerDetail>(`/admin/purchases/${ledgerId}`)));
        setTotalPages(1);
      } else {
        const result = await adminJson<Page<UnmatchedItem>>(`/admin/purchases/items/unmatched?page=${page}&size=20`);
        setItems(result.content);
        setTotalPages(result.totalPages);
      }
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [ledgerId, page]);

  useEffect(() => {
    void load();
  }, [load]);

  async function autoMatch() {
    setMessage(null);
    try {
      if (ledgerId) {
        await matchItems({ ledgerId, action: "AUTO" });
      } else {
        for (const id of new Set(items.map((item) => item.ledgerId))) {
          await matchItems({ ledgerId: id, action: "AUTO" });
        }
      }
      setMessage("자동 매칭을 실행했습니다. 정확히 일치하는 항목만 매칭되며, 나머지는 후보를 확인해 주세요.");
      void load();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <>
      <PageHeader
        title="매입 상품 매칭"
        description="매입 품목을 쇼핑몰 상품 옵션(SKU)과 연결합니다. 거래처 상품코드 → SKU 코드 → 상품명+옵션 순으로 정확히 일치하면 자동 매칭되고, 그 외에는 관리자가 후보를 선택합니다. 매칭된 품목만 입고 등록할 수 있습니다."
        actions={
          <>
            {ledgerId ? (
              <Link href={`/admin/purchases/${ledgerId}`} className={buttonClass}>
                매입내역으로
              </Link>
            ) : null}
            <button type="button" className={primaryButtonClass} disabled={items.length === 0} onClick={() => void autoMatch()}>
              자동 매칭 실행
            </button>
          </>
        }
      />
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {message ? <Notice tone="ok">{message}</Notice> : null}
      {items.length === 0 ? (
        <Notice tone="ok">매칭이 필요한 품목이 없습니다.</Notice>
      ) : (
        <ul className="flex flex-col gap-3">
          {items.map((item) => (
            <MatchRow key={item.itemId} item={item} onDone={(text) => { setMessage(text); void load(); }} onError={setError} />
          ))}
        </ul>
      )}
      {!ledgerId ? <Pager page={page} totalPages={totalPages} onPage={setPage} /> : null}
    </>
  );
}

function MatchRow({
  item,
  onDone,
  onError,
}: {
  item: UnmatchedItem;
  onDone: (message: string) => void;
  onError: (message: string) => void;
}) {
  const [candidates, setCandidates] = useState<Candidate[] | null>(null);
  const [skuId, setSkuId] = useState<number | null>(null);
  const [remember, setRemember] = useState(true);
  const [busy, setBusy] = useState(false);

  async function loadCandidates() {
    try {
      const list = await adminJson<Candidate[]>(`/admin/purchases/items/${item.itemId}/candidates`);
      setCandidates(list);
      const best = list[0]?.skus.find((s) => s.optionMatches);
      if (best) setSkuId(best.skuId);
    } catch (e) {
      onError(errorMessage(e));
    }
  }

  async function act(action: "CONFIRM" | "NEW_PRODUCT" | "IGNORE") {
    setBusy(true);
    try {
      if (action === "CONFIRM") {
        const candidate = candidates?.find((c) => c.skus.some((s) => s.skuId === skuId));
        if (!candidate || skuId === null) {
          onError("매칭할 옵션(SKU)을 선택해 주세요.");
          return;
        }
        await matchItems({ itemId: item.itemId, action, productId: candidate.productId, skuId, remember });
        onDone(`'${item.productName}' 품목을 ${candidate.productName}에 매칭했습니다.`);
      } else {
        await matchItems({ itemId: item.itemId, action });
        onDone(action === "IGNORE" ? `'${item.productName}' 품목을 매칭 대상에서 제외했습니다.` : `'${item.productName}' 품목을 신규 상품 후보로 표시했습니다.`);
      }
    } catch (e) {
      onError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="flex flex-col gap-2 rounded-xl border border-border bg-surface p-3">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <Link href={`/admin/purchases/${item.ledgerId}`} className="text-xs text-muted-foreground underline">
          {item.managementNo}
        </Link>
        <span className="text-xs text-muted-foreground">
          {item.purchaseDate} · {item.supplierName ?? "-"}
        </span>
        <strong>{item.productName}</strong>
        <span>{[item.color, item.size].filter(Boolean).join(" / ")}</span>
        {item.supplierProductCode ? <Badge>코드 {item.supplierProductCode}</Badge> : null}
        <span className="tabular-nums">
          {item.quantity ?? "-"}개 × {won(item.unitPrice)}
        </span>
        <Badge tone="warn">{MATCH_STATUS_LABELS[item.matchStatus] ?? item.matchStatus}</Badge>
        {item.matchNote ? <span className="text-xs text-muted-foreground">{item.matchNote}</span> : null}
      </div>
      <div className="flex flex-wrap gap-2">
        <button type="button" className={buttonClass} onClick={() => void loadCandidates()}>
          후보 상품 보기
        </button>
        <Link href={`/admin/products/new?ledgerItemId=${item.itemId}`} className={buttonClass}>
          AI 신규 상품등록
        </Link>
        <button type="button" className={buttonClass} disabled={busy} onClick={() => void act("NEW_PRODUCT")}>
          신규 상품 후보로 표시
        </button>
        <button type="button" className={buttonClass} disabled={busy} onClick={() => void act("IGNORE")}>
          매칭 제외
        </button>
      </div>
      {candidates ? (
        candidates.length === 0 ? (
          <p className="text-sm text-muted-foreground">비슷한 상품이 없습니다. 신규 상품으로 등록해 주세요.</p>
        ) : (
          <div className="flex flex-col gap-2">
            {candidates.map((c) => (
              <div key={c.productId} className="rounded-lg border border-border p-2 text-sm">
                <div className="flex flex-wrap items-center gap-2">
                  <Link href={`/admin/products/${c.productId}/edit`} className="font-medium underline" target="_blank">
                    {c.productName}
                  </Link>
                  <Badge>유사도 {Math.round(Number(c.score) * 100)}%</Badge>
                  <span className="text-xs text-muted-foreground">{c.reasons.join(" · ")}</span>
                </div>
                <div className="mt-1 flex flex-wrap gap-2">
                  {c.skus.map((s) => (
                    <label key={s.skuId} className={`flex items-center gap-1 rounded border px-2 py-1 text-xs ${s.optionMatches ? "border-emerald-400" : "border-border"}`}>
                      <input type="radio" name={`sku-${item.itemId}`} checked={skuId === s.skuId} onChange={() => setSkuId(s.skuId)} />
                      {s.color}/{s.size} <span className="text-muted-foreground">재고 {s.stockQuantity}</span>
                    </label>
                  ))}
                </div>
              </div>
            ))}
            <div className="flex flex-wrap items-center gap-3">
              <label className="flex items-center gap-1 text-sm">
                <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
                다음 매입부터 이 거래처 상품코드를 자동 매칭
              </label>
              <button type="button" className={primaryButtonClass} disabled={busy || skuId === null} onClick={() => void act("CONFIRM")}>
                선택한 옵션으로 매칭
              </button>
            </div>
          </div>
        )
      ) : null}
    </li>
  );
}
