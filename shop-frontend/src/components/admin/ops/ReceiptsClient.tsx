"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  adminJson,
  dateTime,
  errorMessage,
  type LedgerDetail,
  type Page,
  postJson,
  query,
  type Receipt,
  RECEIPT_STATUS_LABELS,
  won,
} from "@/features/admin/ops";
import {
  Badge,
  buttonClass,
  dangerButtonClass,
  Field,
  inputClass,
  Notice,
  PageHeader,
  Pager,
  primaryButtonClass,
  Section,
  Table,
  WarningList,
} from "@/components/admin/ops/ui";

type Costs = { freightCost: string; incidentalCost: string; packagingCost: string; otherCost: string; allocationMethod: string };

const COST_FIELDS: [keyof Costs, string][] = [
  ["freightCost", "운송비"],
  ["incidentalCost", "부대비용"],
  ["packagingCost", "포장비"],
  ["otherCost", "기타 매입비용"],
];

function costNumber(value: string): number {
  const n = Number(value.replace(/,/g, ""));
  return Number.isFinite(n) && n > 0 ? Math.round(n) : 0;
}

function CostInputs({ costs, onChange }: { costs: Costs; onChange: (next: Costs) => void }) {
  return (
    <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-5">
      {COST_FIELDS.map(([key, label]) => (
        <Field key={key} label={label}>
          <input inputMode="numeric" className={inputClass} value={costs[key]} onChange={(e) => onChange({ ...costs, [key]: e.target.value })} />
        </Field>
      ))}
      <Field label="배분 기준" hint="매입원가에 포함할 비용만 입력하세요. 판매 배송비·결제수수료는 손익에서 따로 계산합니다.">
        <select className={inputClass} value={costs.allocationMethod} onChange={(e) => onChange({ ...costs, allocationMethod: e.target.value })}>
          <option value="QUANTITY">수량 비율</option>
          <option value="AMOUNT">금액 비율</option>
        </select>
      </Field>
    </div>
  );
}

export function ReceiptsClient({ ledgerId, receiptId }: { ledgerId?: number; receiptId?: number }) {
  const [status, setStatus] = useState("PENDING");
  const [page, setPage] = useState(0);
  const [list, setList] = useState<Page<Receipt> | null>(null);
  const [selected, setSelected] = useState<number | null>(receiptId ?? null);
  const [creating, setCreating] = useState(Boolean(ledgerId));
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setList(await adminJson<Page<Receipt>>(`/admin/inventory/receipts${query({ status, page, size: 20 })}`));
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [status, page]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <>
      <PageHeader
        title="입고 관리"
        description="매입장부의 매칭된 품목으로 입고를 등록하고, 실제 받은 수량을 확인한 뒤 '입고 확정'을 누르면 그때 재고와 원가가 반영됩니다. 같은 입고건은 여러 번 확정해도 한 번만 반영됩니다."
      />
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {ledgerId && creating ? (
        <CreateReceipt
          ledgerId={ledgerId}
          onCreated={(receipt) => {
            setCreating(false);
            setStatus("PENDING");
            setSelected(receipt.id);
            void load();
          }}
        />
      ) : ledgerId ? (
        <Notice>
          입고건이 등록되었습니다. 실제 받은 수량을 확인한 뒤 &lsquo;입고 확정&rsquo;을 눌러 주세요.{" "}
          <button type="button" className="underline" onClick={() => setCreating(true)}>
            같은 매입내역으로 추가 입고 등록
          </button>
        </Notice>
      ) : (
        <Notice>
          새 입고는 <Link href="/admin/purchases" className="underline">매입장부</Link>의 매입내역 상세에서 &lsquo;입고 등록&rsquo;으로 시작합니다.
        </Notice>
      )}
      {selected !== null ? (
        <ReceiptDetail
          key={selected}
          receiptId={selected}
          onChanged={() => void load()}
          onClose={() => setSelected(null)}
        />
      ) : null}
      <Section
        title="입고 목록"
        actions={
          <select className={inputClass} value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
            <option value="">전체</option>
            <option value="PENDING">입고 대기</option>
            <option value="CONFIRMED">입고 확정</option>
            <option value="CANCELLED">취소</option>
          </select>
        }
      >
        <Table>
          <thead>
            <tr>
              <th>입고번호</th>
              <th>매입 관리번호</th>
              <th>거래처</th>
              <th>상태</th>
              <th className="text-right">품목</th>
              <th className="text-right">부대비용</th>
              <th>예정일</th>
              <th>확정일시</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {list?.content.length === 0 ? (
              <tr>
                <td colSpan={9} className="py-6 text-center text-muted-foreground">
                  입고건이 없습니다.
                </td>
              </tr>
            ) : null}
            {list?.content.map((r) => (
              <tr key={r.id}>
                <td>{r.receiptNo}</td>
                <td>
                  <Link className="underline" href={`/admin/purchases/${r.purchaseLedgerId}`}>
                    {r.managementNo}
                  </Link>
                </td>
                <td>{r.supplierName ?? "-"}</td>
                <td>
                  <Badge tone={r.status === "CONFIRMED" ? "ok" : r.status === "PENDING" ? "warn" : "muted"}>
                    {RECEIPT_STATUS_LABELS[r.status] ?? r.status}
                  </Badge>
                </td>
                <td className="text-right tabular-nums">{r.items.length}</td>
                <td className="text-right tabular-nums">{won(r.additionalCostTotal)}</td>
                <td>{r.expectedDate ?? "-"}</td>
                <td>{dateTime(r.confirmedAt)}</td>
                <td>
                  <button type="button" className="text-xs underline" onClick={() => setSelected(r.id)}>
                    {r.status === "PENDING" ? "확인·확정" : "보기"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
        {list ? <Pager page={list.page} totalPages={list.totalPages} onPage={setPage} /> : null}
      </Section>
    </>
  );
}

function CreateReceipt({ ledgerId, onCreated }: { ledgerId: number; onCreated: (receipt: Receipt) => void }) {
  const [detail, setDetail] = useState<LedgerDetail | null>(null);
  const [quantities, setQuantities] = useState<Record<number, string>>({});
  const [costs, setCosts] = useState<Costs>({ freightCost: "", incidentalCost: "", packagingCost: "", otherCost: "", allocationMethod: "QUANTITY" });
  const [expectedDate, setExpectedDate] = useState("");
  const [memo, setMemo] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    adminJson<LedgerDetail>(`/admin/purchases/${ledgerId}`)
      .then((d) => {
        setDetail(d);
        const q: Record<number, string> = {};
        d.items.forEach((item) => {
          if (item.id !== null && item.matchStatus === "MATCHED" && (item.remainingQuantity ?? 0) > 0) {
            q[item.id] = String(item.remainingQuantity);
          }
        });
        setQuantities(q);
      })
      .catch((e) => setError(errorMessage(e)));
  }, [ledgerId]);

  if (!detail) return error ? <Notice tone="danger">{error}</Notice> : null;
  const receivable = detail.items.filter((item) => item.matchStatus === "MATCHED" && (item.remainingQuantity ?? 0) > 0);
  const unmatched = detail.items.filter((item) => item.matchStatus !== "MATCHED" && item.matchStatus !== "IGNORED");

  async function create() {
    setBusy(true);
    setError(null);
    try {
      const receipt = await postJson<Receipt>("/admin/inventory/receipts", {
        purchaseLedgerId: ledgerId,
        expectedDate: expectedDate || null,
        freightCost: costNumber(costs.freightCost),
        incidentalCost: costNumber(costs.incidentalCost),
        packagingCost: costNumber(costs.packagingCost),
        otherCost: costNumber(costs.otherCost),
        allocationMethod: costs.allocationMethod,
        memo: memo || null,
        items: Object.entries(quantities)
          .filter(([, v]) => v !== "")
          .map(([id, v]) => ({ purchaseLedgerItemId: Number(id), expectedQuantity: Number(v) })),
      });
      onCreated(receipt);
    } catch (e) {
      setError(errorMessage(e, "입고 등록에 실패했습니다."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Section title={`입고 등록 — ${detail.ledger.managementNo} (${detail.ledger.supplierName ?? "-"})`}>
      {unmatched.length > 0 ? (
        <Notice tone="warn">
          매칭되지 않은 품목 {unmatched.length}개는 입고할 수 없습니다.{" "}
          <Link href={`/admin/purchases/matching?ledgerId=${ledgerId}`} className="underline">
            상품 매칭하기
          </Link>
        </Notice>
      ) : null}
      {receivable.length === 0 ? (
        <p className="text-sm text-muted-foreground">입고할 수 있는 품목이 없습니다. (모두 입고되었거나 매칭 전입니다)</p>
      ) : (
        <>
          <Table>
            <thead>
              <tr>
                <th>품목</th>
                <th>매칭 상품</th>
                <th className="text-right">매입 수량</th>
                <th className="text-right">기입고</th>
                <th className="text-right">이번 입고 예정</th>
              </tr>
            </thead>
            <tbody>
              {receivable.map((item) => (
                <tr key={item.id}>
                  <td>
                    {item.productName} {[item.color, item.size].filter(Boolean).join("/")}
                  </td>
                  <td>
                    {item.matchedProductName} {item.matchedOptionName ? `(${item.matchedOptionName})` : ""}
                  </td>
                  <td className="text-right tabular-nums">{item.quantity}</td>
                  <td className="text-right tabular-nums">{item.receivedQuantity ?? 0}</td>
                  <td className="text-right">
                    <input
                      inputMode="numeric"
                      aria-label={`${item.productName} 이번 입고 예정 수량`}
                      className="h-8 w-20 rounded border border-border bg-surface px-2 text-right"
                      value={quantities[item.id as number] ?? ""}
                      onChange={(e) => setQuantities((prev) => ({ ...prev, [item.id as number]: e.target.value.replace(/\D/g, "") }))}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
          <CostInputs costs={costs} onChange={setCosts} />
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="입고 예정일">
              <input type="date" className={inputClass} value={expectedDate} onChange={(e) => setExpectedDate(e.target.value)} />
            </Field>
            <Field label="메모">
              <input className={inputClass} value={memo} onChange={(e) => setMemo(e.target.value)} />
            </Field>
          </div>
          {error ? <Notice tone="danger">{error}</Notice> : null}
          <div>
            <button type="button" className={primaryButtonClass} disabled={busy} onClick={() => void create()}>
              입고 등록 (재고 반영 전)
            </button>
          </div>
        </>
      )}
    </Section>
  );
}

function ReceiptDetail({ receiptId, onChanged, onClose }: { receiptId: number; onChanged: () => void; onClose: () => void }) {
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [received, setReceived] = useState<Record<number, string>>({});
  const [costs, setCosts] = useState<Costs | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<Receipt | null>(null);
  const [busy, setBusy] = useState(false);

  const apply = useCallback((r: Receipt) => {
    setReceipt(r);
    const q: Record<number, string> = {};
    r.items.forEach((item) => {
      q[item.id] = String(item.receivedQuantity ?? item.expectedQuantity);
    });
    setReceived(q);
    setCosts({
      freightCost: String(r.freightCost || ""),
      incidentalCost: String(r.incidentalCost || ""),
      packagingCost: String(r.packagingCost || ""),
      otherCost: String(r.otherCost || ""),
      allocationMethod: r.allocationMethod ?? "QUANTITY",
    });
  }, []);

  useEffect(() => {
    adminJson<Receipt>(`/admin/inventory/receipts/${receiptId}`)
      .then(apply)
      .catch((e) => setError(errorMessage(e)));
  }, [receiptId, apply]);

  if (!receipt || !costs) return error ? <Notice tone="danger">{error}</Notice> : null;
  const pending = receipt.status === "PENDING";
  const mismatches = receipt.items.filter((item) => Number(received[item.id] ?? 0) !== item.expectedQuantity);

  async function confirm() {
    if (!receipt || !costs) return;
    const text =
      mismatches.length > 0
        ? `예정 수량과 다른 품목이 ${mismatches.length}개 있습니다. 입력한 실제 수량으로 입고를 확정할까요? 확정 후에는 재고가 즉시 증가합니다.`
        : "입고를 확정할까요? 확정 후에는 재고와 원가가 즉시 반영됩니다.";
    if (!window.confirm(text)) return;
    setBusy(true);
    setError(null);
    try {
      const next = await postJson<Receipt>(`/admin/inventory/receipts/${receipt.id}/confirm`, {
        items: receipt.items.map((item) => ({ receiptItemId: item.id, receivedQuantity: Number(received[item.id] ?? 0) })),
        freightCost: costNumber(costs.freightCost),
        incidentalCost: costNumber(costs.incidentalCost),
        packagingCost: costNumber(costs.packagingCost),
        otherCost: costNumber(costs.otherCost),
        allocationMethod: costs.allocationMethod,
      });
      setResult(next);
      apply(next);
      onChanged();
    } catch (e) {
      setError(errorMessage(e, "입고 확정에 실패했습니다."));
    } finally {
      setBusy(false);
    }
  }

  async function cancel() {
    if (!receipt || !window.confirm("입고 대기 건을 취소할까요? 재고에는 영향이 없습니다.")) return;
    try {
      apply(await postJson<Receipt>(`/admin/inventory/receipts/${receipt.id}/cancel`, {}));
      onChanged();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <Section
      title={`입고 ${receipt.receiptNo} — ${RECEIPT_STATUS_LABELS[receipt.status] ?? receipt.status}`}
      actions={
        <button type="button" className={buttonClass} onClick={onClose}>
          닫기
        </button>
      }
    >
      <p className="text-sm text-muted-foreground">
        매입{" "}
        <Link href={`/admin/purchases/${receipt.purchaseLedgerId}`} className="underline">
          {receipt.managementNo}
        </Link>{" "}
        · {receipt.supplierName ?? "-"} · 등록 {dateTime(receipt.createdAt)}
        {receipt.confirmedAt ? ` · 확정 ${dateTime(receipt.confirmedAt)}` : ""}
      </p>
      {result?.alreadyConfirmed ? <Notice tone="warn">이미 확정된 입고건입니다. 재고는 다시 증가하지 않았습니다.</Notice> : null}
      {result && !result.alreadyConfirmed ? <Notice tone="ok">입고를 확정했습니다. 재고와 원가 이력이 반영되었습니다.</Notice> : null}
      <WarningList items={receipt.warnings} />
      <Table>
        <thead>
          <tr>
            <th>상품 / 옵션</th>
            <th className="text-right">매입 수량</th>
            <th className="text-right">입고 예정</th>
            <th className="text-right">실제 입고</th>
            <th className="text-right">매입 단가</th>
            <th className="text-right">배분 비용</th>
            <th className="text-right">입고 원가(개당)</th>
            <th className="text-right">현재 재고</th>
          </tr>
        </thead>
        <tbody>
          {receipt.items.map((item) => {
            const value = received[item.id] ?? "";
            const diff = Number(value) !== item.expectedQuantity;
            return (
              <tr key={item.id}>
                <td>
                  <Link href={`/admin/products/${item.productId}/edit`} className="underline">
                    {item.productName}
                  </Link>{" "}
                  <span className="text-muted-foreground">{item.optionName}</span>
                </td>
                <td className="text-right tabular-nums">{item.orderedQuantity}</td>
                <td className="text-right tabular-nums">{item.expectedQuantity}</td>
                <td className="text-right">
                  {pending ? (
                    <input
                      inputMode="numeric"
                      aria-label={`${item.productName} 실제 입고 수량`}
                      className={`h-8 w-20 rounded border px-2 text-right ${diff ? "border-amber-400 bg-amber-50" : "border-border bg-surface"}`}
                      value={value}
                      onChange={(e) => setReceived((prev) => ({ ...prev, [item.id]: e.target.value.replace(/\D/g, "") }))}
                    />
                  ) : (
                    <span className="tabular-nums">{item.receivedQuantity ?? "-"}</span>
                  )}
                </td>
                <td className="text-right tabular-nums">{won(item.unitCost)}</td>
                <td className="text-right tabular-nums">{won(item.allocatedCost)}</td>
                <td className="text-right tabular-nums">{won(item.landedUnitCost)}</td>
                <td className="text-right tabular-nums">{item.currentStock}</td>
              </tr>
            );
          })}
        </tbody>
      </Table>
      {pending ? (
        <>
          <CostInputs costs={costs} onChange={setCosts} />
          {error ? <Notice tone="danger">{error}</Notice> : null}
          <div className="flex gap-2">
            <button type="button" className={primaryButtonClass} disabled={busy} onClick={() => void confirm()}>
              {busy ? "처리 중..." : "입고 확정 (재고 반영)"}
            </button>
            <button type="button" className={dangerButtonClass} disabled={busy} onClick={() => void cancel()}>
              입고 취소
            </button>
          </div>
        </>
      ) : (
        <p className="text-sm text-muted-foreground">
          부대비용 합계 {won(receipt.additionalCostTotal)} ({receipt.allocationMethod === "AMOUNT" ? "금액" : "수량"} 비율 배분)
        </p>
      )}
    </Section>
  );
}
