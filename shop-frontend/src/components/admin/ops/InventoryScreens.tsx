"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  adminJson,
  type Analysis,
  COST_SOURCE_LABELS,
  dateTime,
  errorMessage,
  type LowStockRow,
  type Page,
  query,
  type SlowMovingRow,
  type StockStatus,
  type TransactionRow,
  won,
} from "@/features/admin/ops";
import {
  Badge,
  buttonClass,
  inputClass,
  Notice,
  PageHeader,
  Pager,
  primaryButtonClass,
  Section,
  StatCard,
  Table,
} from "@/components/admin/ops/ui";

const MOVEMENT_TYPES: [string, string][] = [
  ["RECEIPT", "매입입고"],
  ["SALE", "주문출고"],
  ["CANCEL", "주문취소 복구"],
  ["RETURN", "반품입고"],
  ["ADJUSTMENT", "재고조정"],
  ["DAMAGE", "파손 및 폐기"],
  ["RESERVATION", "주문 예약"],
  ["RELEASE", "예약 해제"],
];

const STOCK_STATUS: Record<string, { label: string; tone: "danger" | "warn" | "ok" }> = {
  SOLD_OUT: { label: "품절", tone: "danger" },
  LOW: { label: "부족", tone: "warn" },
  NORMAL: { label: "정상", tone: "ok" },
};

export function StockStatusClient() {
  const [keyword, setKeyword] = useState("");
  const [filter, setFilter] = useState("");
  const [applied, setApplied] = useState({ keyword: "", filter: "" });
  const [data, setData] = useState<StockStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    adminJson<StockStatus>(`/admin/inventory/status${query(applied)}`)
      .then(setData)
      .catch((e) => setError(errorMessage(e)));
  }, [applied]);

  return (
    <>
      <PageHeader
        title="재고 현황"
        description="옵션(SKU)별 실재고, 예약, 판매 가능 수량과 재고 금액입니다. 재고 금액은 가격 정책의 원가 계산 방식으로 평가하며, 원가 이력이 없으면 상품의 매입가를 씁니다."
        actions={
          <Link href="/admin/inventory" className={buttonClass}>
            옵션 재고 직접 수정
          </Link>
        }
      />
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {data ? (
        <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
          <StatCard label="SKU 수" value={data.skuCount.toLocaleString("ko-KR")} />
          <StatCard label="총 재고 수량" value={data.totalQuantity.toLocaleString("ko-KR")} />
          <StatCard label="재고 금액" value={won(data.totalValue)} />
          <StatCard label="품절" value={data.soldOutCount} tone={data.soldOutCount ? "danger" : "ok"} />
          <StatCard label="재고 부족" value={data.lowStockCount} tone={data.lowStockCount ? "warn" : "ok"} href="/admin/inventory/analysis" />
          <StatCard label="원가 미확인" value={data.unknownCostCount} tone={data.unknownCostCount ? "warn" : undefined} />
        </div>
      ) : null}
      <form
        className="flex flex-wrap gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          setApplied({ keyword, filter });
        }}
      >
        <input className={inputClass} placeholder="상품명·SKU 코드" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
        <select className={inputClass} value={filter} onChange={(e) => setFilter(e.target.value)}>
          <option value="">전체</option>
          <option value="SOLD_OUT">품절</option>
          <option value="LOW">부족</option>
          <option value="NORMAL">정상</option>
        </select>
        <button type="submit" className={primaryButtonClass}>
          조회
        </button>
      </form>
      <Table>
        <thead>
          <tr>
            <th>상품</th>
            <th>SKU</th>
            <th>옵션</th>
            <th className="text-right">실재고</th>
            <th className="text-right">예약</th>
            <th className="text-right">판매 가능</th>
            <th className="text-right">입고 예정</th>
            <th className="text-right">재주문점</th>
            <th className="text-right">단위 원가</th>
            <th className="text-right">재고 금액</th>
            <th>상태</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {data?.rows.map((row) => (
            <tr key={row.skuId}>
              <td>
                <Link href={`/admin/products/${row.productId}/edit`} className="underline">
                  {row.productName}
                </Link>
              </td>
              <td className="text-xs">{row.skuCode}</td>
              <td>{row.optionName}</td>
              <td className="text-right tabular-nums">{row.stockQuantity}</td>
              <td className="text-right tabular-nums">{row.reservedQuantity}</td>
              <td className="text-right tabular-nums">{row.availableQuantity}</td>
              <td className="text-right tabular-nums">{row.pendingInbound || "-"}</td>
              <td className="text-right tabular-nums">{row.reorderPoint}</td>
              <td className="text-right tabular-nums" title={row.costSource ? COST_SOURCE_LABELS[row.costSource] ?? row.costSource : ""}>
                {row.unitCost === null ? <Badge tone="warn">미확인</Badge> : won(row.unitCost)}
              </td>
              <td className="text-right tabular-nums">{won(row.stockValue)}</td>
              <td>
                <Badge tone={STOCK_STATUS[row.stockStatus]?.tone}>{STOCK_STATUS[row.stockStatus]?.label ?? row.stockStatus}</Badge>
              </td>
              <td>
                <Link href={`/admin/inventory/transactions?skuId=${row.skuId}`} className="text-xs underline">
                  수불 내역
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </Table>
    </>
  );
}

export function TransactionsClient({ skuId }: { skuId?: number }) {
  const [draft, setDraft] = useState({ type: "", from: "", to: "", keyword: "" });
  const [applied, setApplied] = useState(draft);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<TransactionRow> | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setData(await adminJson<Page<TransactionRow>>(`/admin/inventory/transactions${query({ ...applied, skuId, page, size: 30 })}`));
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [applied, skuId, page]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <>
      <PageHeader
        title="재고 수불부"
        description="모든 재고 변동을 변경 전·후 수량과 함께 기록합니다. 매입입고는 입고 확정 시점에만 생깁니다."
        actions={skuId ? <Link href="/admin/inventory/transactions" className={buttonClass}>전체 보기</Link> : null}
      />
      <form
        className="flex flex-wrap gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          setApplied(draft);
        }}
      >
        <select className={inputClass} value={draft.type} onChange={(e) => setDraft((p) => ({ ...p, type: e.target.value }))}>
          <option value="">전체 유형</option>
          {MOVEMENT_TYPES.map(([k, v]) => (
            <option key={k} value={k}>
              {v}
            </option>
          ))}
        </select>
        <input type="date" aria-label="시작일" className={inputClass} value={draft.from} onChange={(e) => setDraft((p) => ({ ...p, from: e.target.value }))} />
        <input type="date" aria-label="종료일" className={inputClass} value={draft.to} onChange={(e) => setDraft((p) => ({ ...p, to: e.target.value }))} />
        <input className={inputClass} placeholder="상품명·SKU·참조번호" value={draft.keyword} onChange={(e) => setDraft((p) => ({ ...p, keyword: e.target.value }))} />
        <button type="submit" className={primaryButtonClass}>
          조회
        </button>
      </form>
      {error ? <Notice tone="danger">{error}</Notice> : null}
      <Table>
        <thead>
          <tr>
            <th>일시</th>
            <th>유형</th>
            <th>상품</th>
            <th>옵션</th>
            <th className="text-right">수량</th>
            <th className="text-right">변경 전</th>
            <th className="text-right">변경 후</th>
            <th>참조</th>
            <th className="text-right">단가</th>
            <th>사유</th>
            <th>처리자</th>
          </tr>
        </thead>
        <tbody>
          {data?.content.length === 0 ? (
            <tr>
              <td colSpan={11} className="py-6 text-center text-muted-foreground">
                내역이 없습니다.
              </td>
            </tr>
          ) : null}
          {data?.content.map((row) => (
            <tr key={row.id}>
              <td className="whitespace-nowrap">{dateTime(row.createdAt)}</td>
              <td>{row.movementLabel}</td>
              <td>{row.productName}</td>
              <td>{row.optionName}</td>
              <td className={`text-right tabular-nums ${row.quantity < 0 ? "text-danger" : ""}`}>
                {row.quantity > 0 ? `+${row.quantity}` : row.quantity}
              </td>
              <td className="text-right tabular-nums">{row.stockBefore ?? "-"}</td>
              <td className="text-right tabular-nums">{row.stockAfter ?? "-"}</td>
              <td className="text-xs">
                {row.receiptNo ? (
                  <Link className="underline" href={`/admin/inventory/receipts?receiptId=${row.purchaseReceiptId}`}>
                    {row.receiptNo}
                  </Link>
                ) : (
                  [row.referenceType, row.referenceId].filter(Boolean).join(" ") || "-"
                )}
                {row.managementNo ? <span className="text-muted-foreground"> ({row.managementNo})</span> : null}
              </td>
              <td className="text-right tabular-nums">{won(row.unitCost)}</td>
              <td className="max-w-[14rem] truncate" title={row.reason ?? ""}>
                {row.reason ?? "-"}
              </td>
              <td>{row.actorName ?? (row.actorMemberId ? `#${row.actorMemberId}` : "시스템")}</td>
            </tr>
          ))}
        </tbody>
      </Table>
      {data ? <Pager page={data.page} totalPages={data.totalPages} onPage={setPage} /> : null}
    </>
  );
}

export function StockAnalysisClient() {
  const [low, setLow] = useState<Analysis<LowStockRow> | null>(null);
  const [slow, setSlow] = useState<Analysis<SlowMovingRow> | null>(null);
  const [days, setDays] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    adminJson<Analysis<LowStockRow>>("/admin/inventory/low-stock")
      .then(setLow)
      .catch((e) => setError(errorMessage(e)));
  }, []);

  const loadSlow = useCallback(() => {
    adminJson<Analysis<SlowMovingRow>>(`/admin/inventory/slow-moving${query({ days })}`)
      .then(setSlow)
      .catch((e) => setError(errorMessage(e)));
  }, [days]);

  useEffect(() => {
    loadSlow();
    // only on mount; later reloads are explicit
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <>
      <PageHeader
        title="부족 · 장기 재고 분석"
        description="판매 속도, 리드타임, 안전재고로 계산한 규칙 기반 분석입니다. 발주는 자동으로 실행되지 않으며, 추천 수량은 참고용입니다."
        actions={
          <Link href="/admin/ai/recommendations" className={buttonClass}>
            AI 발주 추천
          </Link>
        }
      />
      {error ? <Notice tone="danger">{error}</Notice> : null}
      <Section title={`재고 부족 / 발주 검토 (${low?.rows.length ?? 0})`}>
        {low ? (
          <ul className="list-disc pl-4 text-xs text-muted-foreground">
            {low.criteria.map((c, i) => (
              <li key={i}>{c}</li>
            ))}
          </ul>
        ) : null}
        <Table>
          <thead>
            <tr>
              <th>상품 / 옵션</th>
              <th className="text-right">판매 가능</th>
              <th className="text-right">입고 예정</th>
              <th className="text-right">기간 판매</th>
              <th className="text-right">일 판매</th>
              <th className="text-right">소진 예상(일)</th>
              <th className="text-right">리드타임</th>
              <th className="text-right">안전재고</th>
              <th className="text-right">추천 수량</th>
              <th>거래처</th>
              <th>사유</th>
            </tr>
          </thead>
          <tbody>
            {low?.rows.length === 0 ? (
              <tr>
                <td colSpan={11} className="py-6 text-center text-muted-foreground">
                  부족한 재고가 없습니다.
                </td>
              </tr>
            ) : null}
            {low?.rows.map((row) => (
              <tr key={row.skuId}>
                <td>
                  {row.productName} <span className="text-muted-foreground">{row.optionName}</span>
                </td>
                <td className="text-right tabular-nums">{row.availableQuantity}</td>
                <td className="text-right tabular-nums">{row.pendingInbound || "-"}</td>
                <td className="text-right tabular-nums">
                  {row.salesInWindow} <span className="text-xs text-muted-foreground">/{row.windowDays}일</span>
                </td>
                <td className="text-right tabular-nums">{Number(row.dailySales).toFixed(2)}</td>
                <td className="text-right tabular-nums">{row.coverDays === null ? "-" : Number(row.coverDays).toFixed(1)}</td>
                <td className="text-right tabular-nums">{row.leadDays}일</td>
                <td className="text-right tabular-nums">{row.safetyStock}</td>
                <td className="text-right font-semibold tabular-nums">{row.suggestedQuantity}</td>
                <td>{row.supplierName ?? "-"}</td>
                <td className="text-xs">{row.reasons.join(" · ")}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      </Section>
      <Section
        title={`장기 재고 (${slow?.rows.length ?? 0})`}
        actions={
          <div className="flex gap-2">
            <input className={`${inputClass} w-28`} placeholder={`기간(일) ${slow?.windowDays ?? ""}`} inputMode="numeric" value={days} onChange={(e) => setDays(e.target.value.replace(/\D/g, ""))} />
            <button type="button" className={buttonClass} onClick={loadSlow}>
              다시 분석
            </button>
          </div>
        }
      >
        {slow ? (
          <ul className="list-disc pl-4 text-xs text-muted-foreground">
            {slow.criteria.map((c, i) => (
              <li key={i}>{c}</li>
            ))}
          </ul>
        ) : null}
        <Table>
          <thead>
            <tr>
              <th>상품 / 옵션</th>
              <th className="text-right">재고</th>
              <th className="text-right">기간 판매</th>
              <th>마지막 판매</th>
              <th>마지막 입고</th>
              <th className="text-right">미판매 일수</th>
              <th className="text-right">재고 금액</th>
              <th>판단</th>
            </tr>
          </thead>
          <tbody>
            {slow?.rows.length === 0 ? (
              <tr>
                <td colSpan={8} className="py-6 text-center text-muted-foreground">
                  장기 재고가 없습니다.
                </td>
              </tr>
            ) : null}
            {slow?.rows.map((row) => (
              <tr key={row.skuId}>
                <td>
                  {row.productName} <span className="text-muted-foreground">{row.optionName}</span>
                </td>
                <td className="text-right tabular-nums">{row.stockQuantity}</td>
                <td className="text-right tabular-nums">
                  {row.salesInPeriod} <span className="text-xs text-muted-foreground">/{row.periodDays}일</span>
                </td>
                <td>{row.lastSoldDate ?? "판매 없음"}</td>
                <td>{row.lastReceivedDate ?? "-"}</td>
                <td className="text-right tabular-nums">{row.daysSinceLastSale ?? "-"}</td>
                <td className="text-right tabular-nums">{won(row.stockValue)}</td>
                <td className="text-xs">{row.reason}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      </Section>
    </>
  );
}
