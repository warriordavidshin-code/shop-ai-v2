"use client";

import { useState } from "react";
import {
  type DuplicateCandidate,
  errorMessage,
  type Evidence,
  EVIDENCE_CATEGORY_LABELS,
  EVIDENCE_TYPE_LABELS,
  KEPT_LABELS,
  type LedgerDetail,
  type LedgerItem,
  type LedgerRequest,
  MATCH_STATUS_LABELS,
  PAYMENT_METHOD_LABELS,
  postForm,
  postJson,
  won,
} from "@/features/admin/ops";
import { openEvidence } from "@/components/admin/ops/EvidencePreview";
import {
  Badge,
  buttonClass,
  dangerButtonClass,
  Field,
  inputClass,
  Notice,
  primaryButtonClass,
  Section,
} from "@/components/admin/ops/ui";

export function emptyLedgerRequest(): LedgerRequest {
  return {
    purchaseDate: new Date().toISOString().slice(0, 10),
    supplierId: null,
    supplierName: "",
    supplierBusinessNo: "",
    itemSummary: "",
    purchaseAmount: null,
    supplyAmount: null,
    taxAmount: null,
    paymentAmount: null,
    paymentDate: null,
    paymentMethod: null,
    recipientName: "",
    transferReference: "",
    statementKept: null,
    transferKept: null,
    evidenceType: null,
    evidenceLocation: "",
    remarks: "",
    items: [],
    evidenceIds: [],
    sourceAnalysisId: null,
    confirmDuplicate: false,
    version: null,
  };
}

export function requestFromDetail(detail: LedgerDetail): LedgerRequest {
  const l = detail.ledger;
  return {
    purchaseDate: l.purchaseDate,
    supplierId: l.supplierId,
    supplierName: l.supplierName ?? "",
    supplierBusinessNo: l.supplierBusinessNo ?? "",
    itemSummary: l.itemSummary ?? "",
    purchaseAmount: l.purchaseAmount,
    supplyAmount: l.supplyAmount,
    taxAmount: l.taxAmount,
    paymentAmount: l.paymentAmount,
    paymentDate: l.paymentDate,
    paymentMethod: l.paymentMethod,
    recipientName: l.recipientName ?? "",
    transferReference: l.transferReference ?? "",
    statementKept: l.statementKept,
    transferKept: l.transferKept,
    evidenceType: l.evidenceType,
    evidenceLocation: l.evidenceLocation ?? "",
    remarks: l.remarks ?? "",
    items: detail.items,
    evidenceIds: detail.evidences.map((e) => e.id),
    sourceAnalysisId: detail.sourceAnalysisId,
    confirmDuplicate: false,
    version: l.version,
  };
}

const blankItem = (): LedgerItem => ({
  id: null,
  productName: "",
  optionName: null,
  color: "",
  size: "",
  supplierProductCode: "",
  quantity: null,
  unitPrice: null,
  supplyAmount: null,
  taxAmount: null,
  totalAmount: null,
});

function toNumber(value: string): number | null {
  const text = value.replace(/,/g, "").trim();
  if (text === "") return null;
  const n = Number(text);
  return Number.isFinite(n) ? Math.round(n) : null;
}

function blankToNull(value: string | null | undefined): string | null {
  return value === null || value === undefined || value.trim() === "" ? null : value.trim();
}

function cleanRequest(form: LedgerRequest, confirmDuplicate: boolean): LedgerRequest {
  return {
    ...form,
    supplierName: blankToNull(form.supplierName),
    supplierBusinessNo: blankToNull(form.supplierBusinessNo),
    itemSummary: blankToNull(form.itemSummary),
    recipientName: blankToNull(form.recipientName),
    transferReference: blankToNull(form.transferReference),
    evidenceLocation: blankToNull(form.evidenceLocation),
    remarks: blankToNull(form.remarks),
    paymentDate: blankToNull(form.paymentDate),
    items: form.items
      .filter((item) => item.productName.trim() !== "")
      .map((item) => ({
        ...item,
        productName: item.productName.trim(),
        color: blankToNull(item.color),
        size: blankToNull(item.size),
        optionName: blankToNull(item.optionName),
        supplierProductCode: blankToNull(item.supplierProductCode),
      })),
    confirmDuplicate,
  };
}

export function PurchaseLedgerForm({
  ledgerId,
  initial,
  initialEvidences = [],
  reviewNotes = {},
  submitLabel,
  onSaved,
}: {
  ledgerId?: number;
  initial: LedgerRequest;
  initialEvidences?: Evidence[];
  reviewNotes?: Record<string, string[]>;
  submitLabel?: string;
  onSaved: (detail: LedgerDetail) => void;
}) {
  const [form, setForm] = useState<LedgerRequest>(initial);
  const [evidences, setEvidences] = useState<Evidence[]>(initialEvidences);
  const [uploadCategory, setUploadCategory] = useState("STATEMENT");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [duplicates, setDuplicates] = useState<DuplicateCandidate[] | null>(null);

  const difference =
    form.purchaseAmount === null && form.paymentAmount === null
      ? null
      : (form.purchaseAmount ?? 0) - (form.paymentAmount ?? 0);
  const itemTotal = form.items.reduce((sum, item) => sum + (item.totalAmount ?? 0), 0);

  function setText<K extends keyof LedgerRequest>(key: K) {
    return (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
      setForm((prev) => ({ ...prev, [key]: e.target.value === "" && isNullableSelect(key) ? null : e.target.value }));
  }

  function setAmount(key: "purchaseAmount" | "supplyAmount" | "taxAmount" | "paymentAmount") {
    return (e: React.ChangeEvent<HTMLInputElement>) => setForm((prev) => ({ ...prev, [key]: toNumber(e.target.value) }));
  }

  function updateItem(index: number, patch: Partial<LedgerItem>) {
    setForm((prev) => {
      const items = prev.items.map((item, i) => {
        if (i !== index) return item;
        const next = { ...item, ...patch };
        if (("quantity" in patch || "unitPrice" in patch) && next.quantity !== null && next.unitPrice !== null) {
          next.totalAmount = next.quantity * next.unitPrice;
        }
        return next;
      });
      return { ...prev, items };
    });
  }

  function note(field: string) {
    const notes = reviewNotes[field];
    if (!notes || notes.length === 0) return undefined;
    return <span className="text-amber-700">{notes.join(" / ")}</span>;
  }

  function flagged(field: string) {
    return reviewNotes[field]?.length ? " border-amber-400 bg-amber-50" : "";
  }

  async function upload(files: FileList | null) {
    if (!files || files.length === 0) return;
    setError(null);
    const body = new FormData();
    Array.from(files).forEach((file) => body.append("files", file));
    body.append("category", uploadCategory);
    if (ledgerId) body.append("ledgerId", String(ledgerId));
    try {
      const uploaded = await postForm<Evidence[]>("/admin/purchases/evidence", body);
      setEvidences((prev) => [...prev, ...uploaded]);
      setForm((prev) => ({ ...prev, evidenceIds: [...prev.evidenceIds, ...uploaded.map((e) => e.id)] }));
    } catch (e) {
      setError(errorMessage(e, "증빙 업로드에 실패했습니다."));
    }
  }

  async function changeCategory(evidence: Evidence, category: string) {
    try {
      const updated = await postJson<Evidence>(`/admin/purchases/evidence/${evidence.id}`, { category }, "PATCH");
      setEvidences((prev) => prev.map((e) => (e.id === evidence.id ? updated : e)));
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  function detach(evidence: Evidence) {
    setEvidences((prev) => prev.filter((e) => e.id !== evidence.id));
    setForm((prev) => ({ ...prev, evidenceIds: prev.evidenceIds.filter((id) => id !== evidence.id) }));
  }

  async function save(confirmDuplicate: boolean) {
    setBusy(true);
    setError(null);
    try {
      const payload = cleanRequest(form, confirmDuplicate);
      if (!confirmDuplicate) {
        const found = await postJson<DuplicateCandidate[]>("/admin/purchases/duplicates-check", {
          excludeId: ledgerId ?? null,
          supplierName: payload.supplierName,
          purchaseDate: payload.purchaseDate,
          purchaseAmount: payload.purchaseAmount,
          paymentAmount: payload.paymentAmount,
          transferReference: payload.transferReference,
          evidenceIds: payload.evidenceIds,
        });
        if (found.length > 0) {
          setDuplicates(found);
          return;
        }
      }
      const detail = ledgerId
        ? await postJson<LedgerDetail>(`/admin/purchases/${ledgerId}`, payload, "PUT")
        : await postJson<LedgerDetail>("/admin/purchases", payload);
      setDuplicates(null);
      onSaved(detail);
    } catch (e) {
      setError(errorMessage(e, "저장에 실패했습니다."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(e) => {
        e.preventDefault();
        void save(false);
      }}
    >
      <Section title="기본 정보">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="매입일 *" hint={note("purchaseDate")}>
            <input type="date" required className={inputClass + flagged("purchaseDate")} value={form.purchaseDate} onChange={setText("purchaseDate")} />
          </Field>
          <Field label="거래처(상호)" hint={note("supplierName")}>
            <input className={inputClass + flagged("supplierName")} value={form.supplierName ?? ""} onChange={setText("supplierName")} />
          </Field>
          <Field label="사업자번호" hint={note("supplierBusinessNo")}>
            <input className={inputClass + flagged("supplierBusinessNo")} placeholder="000-00-00000" value={form.supplierBusinessNo ?? ""} onChange={setText("supplierBusinessNo")} />
          </Field>
          <Field label="품목 요약" hint={note("itemSummary") ?? "비워 두면 아래 품목으로 자동 작성됩니다."}>
            <input className={inputClass} value={form.itemSummary ?? ""} onChange={setText("itemSummary")} />
          </Field>
        </div>
      </Section>

      <Section title="금액 · 지급">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="매입액(합계)" hint={note("purchaseAmount")}>
            <input inputMode="numeric" className={inputClass + flagged("purchaseAmount")} value={form.purchaseAmount ?? ""} onChange={setAmount("purchaseAmount")} />
          </Field>
          <Field label="공급가액" hint={note("supplyAmount")}>
            <input inputMode="numeric" className={inputClass + flagged("supplyAmount")} value={form.supplyAmount ?? ""} onChange={setAmount("supplyAmount")} />
          </Field>
          <Field label="세액" hint={note("taxAmount")}>
            <input inputMode="numeric" className={inputClass + flagged("taxAmount")} value={form.taxAmount ?? ""} onChange={setAmount("taxAmount")} />
          </Field>
          <Field label="지급액" hint={note("paymentAmount")}>
            <input inputMode="numeric" className={inputClass + flagged("paymentAmount")} value={form.paymentAmount ?? ""} onChange={setAmount("paymentAmount")} />
          </Field>
          <Field label="지급일" hint={note("paymentDate")}>
            <input type="date" className={inputClass + flagged("paymentDate")} value={form.paymentDate ?? ""} onChange={setText("paymentDate")} />
          </Field>
          <Field label="지급방법" hint={note("paymentMethod")}>
            <select className={inputClass} value={form.paymentMethod ?? ""} onChange={setText("paymentMethod")}>
              <option value="">선택</option>
              {Object.entries(PAYMENT_METHOD_LABELS).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </Field>
          <Field label="입금자/수취인" hint={note("recipientName")}>
            <input className={inputClass} value={form.recipientName ?? ""} onChange={setText("recipientName")} />
          </Field>
          <Field label="이체 참조(적요)" hint={note("transferReference")}>
            <input className={inputClass} value={form.transferReference ?? ""} onChange={setText("transferReference")} />
          </Field>
        </div>
        <div className="flex flex-wrap gap-4 text-sm">
          <span>
            차액(매입액 − 지급액):{" "}
            <strong className={difference ? "text-danger" : ""}>{difference === null ? "-" : won(difference)}</strong>
          </span>
          {form.items.length > 0 ? (
            <span className={form.purchaseAmount !== null && itemTotal !== form.purchaseAmount ? "text-amber-700" : ""}>
              품목 합계: {won(itemTotal)}
              {form.purchaseAmount !== null && itemTotal !== form.purchaseAmount ? " (매입액과 다릅니다)" : ""}
            </span>
          ) : null}
        </div>
      </Section>

      <Section title="증빙 보관">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="거래명세표">
            <select className={inputClass} value={form.statementKept ?? ""} onChange={setText("statementKept")}>
              <option value="">선택</option>
              {Object.entries(KEPT_LABELS).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </Field>
          <Field label="이체내역">
            <select className={inputClass} value={form.transferKept ?? ""} onChange={setText("transferKept")}>
              <option value="">선택</option>
              {Object.entries(KEPT_LABELS).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </Field>
          <Field label="지출증빙" hint={note("evidenceType")}>
            <select className={inputClass} value={form.evidenceType ?? ""} onChange={setText("evidenceType")}>
              <option value="">선택</option>
              {Object.entries(EVIDENCE_TYPE_LABELS).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </Field>
          <Field label="증빙 보관 위치">
            <input className={inputClass} value={form.evidenceLocation ?? ""} onChange={setText("evidenceLocation")} />
          </Field>
        </div>
        <Field label="비고">
          <textarea className="min-h-16 rounded-lg border border-border bg-surface p-2 text-sm" value={form.remarks ?? ""} onChange={setText("remarks")} />
        </Field>
      </Section>

      <Section
        title={`품목 (${form.items.length})`}
        actions={
          <button type="button" className={buttonClass} onClick={() => setForm((prev) => ({ ...prev, items: [...prev.items, blankItem()] }))}>
            품목 추가
          </button>
        }
      >
        {note("items") ? <Notice tone="warn">{note("items")}</Notice> : null}
        {form.items.length === 0 ? (
          <p className="text-sm text-muted-foreground">품목을 추가하면 입고·상품 매칭에 사용됩니다.</p>
        ) : (
          <div className="flex flex-col gap-2">
            {form.items.map((item, index) => (
              <div key={index} className="grid gap-2 rounded-lg border border-border p-2 sm:grid-cols-[2fr_1fr_1fr_1fr_5rem_7rem_7rem_auto]">
                <input className={inputClass} placeholder="상품명 *" value={item.productName} onChange={(e) => updateItem(index, { productName: e.target.value })} />
                <input className={inputClass} placeholder="색상" value={item.color ?? ""} onChange={(e) => updateItem(index, { color: e.target.value })} />
                <input className={inputClass} placeholder="사이즈" value={item.size ?? ""} onChange={(e) => updateItem(index, { size: e.target.value })} />
                <input className={inputClass} placeholder="거래처 상품코드" value={item.supplierProductCode ?? ""} onChange={(e) => updateItem(index, { supplierProductCode: e.target.value })} />
                <input className={inputClass} placeholder="수량" inputMode="numeric" value={item.quantity ?? ""} onChange={(e) => updateItem(index, { quantity: toNumber(e.target.value) })} />
                <input className={inputClass} placeholder="단가" inputMode="numeric" value={item.unitPrice ?? ""} onChange={(e) => updateItem(index, { unitPrice: toNumber(e.target.value) })} />
                <input className={inputClass} placeholder="금액" inputMode="numeric" value={item.totalAmount ?? ""} onChange={(e) => updateItem(index, { totalAmount: toNumber(e.target.value) })} />
                <div className="flex items-center gap-2">
                  {item.matchStatus ? <Badge tone={item.matchStatus === "MATCHED" ? "ok" : "warn"}>{item.matchStatus === "MATCHED" ? item.matchedProductName ?? "매칭됨" : MATCH_STATUS_LABELS[item.matchStatus] ?? item.matchStatus}</Badge> : null}
                  {(item.receivedQuantity ?? 0) > 0 ? (
                    <span className="text-xs text-muted-foreground">입고 {item.receivedQuantity}</span>
                  ) : (
                    <button
                      type="button"
                      className="text-xs text-danger underline"
                      onClick={() => setForm((prev) => ({ ...prev, items: prev.items.filter((_, i) => i !== index) }))}
                    >
                      삭제
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </Section>

      <Section title={`증빙 파일 (${evidences.length})`}>
        <div className="flex flex-wrap items-center gap-2">
          <select className={inputClass} value={uploadCategory} onChange={(e) => setUploadCategory(e.target.value)}>
            {Object.entries(EVIDENCE_CATEGORY_LABELS).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
          <label className={`${buttonClass} cursor-pointer`}>
            파일 추가 (JPG·PNG·WEBP·PDF)
            <input
              type="file"
              multiple
              accept="image/jpeg,image/png,image/webp,application/pdf"
              className="hidden"
              onChange={(e) => {
                void upload(e.target.files);
                e.target.value = "";
              }}
            />
          </label>
          <span className="text-xs text-muted-foreground">증빙은 비공개 저장소에 보관되며 관리자만 열람할 수 있습니다.</span>
        </div>
        {evidences.length > 0 ? (
          <ul className="flex flex-col gap-1 text-sm">
            {evidences.map((evidence) => (
              <li key={evidence.id} className="flex flex-wrap items-center gap-2 rounded-lg border border-border px-2 py-1.5">
                <span className="min-w-0 flex-1 truncate" title={evidence.originalFileName}>
                  {evidence.originalFileName}
                </span>
                <span className="text-xs text-muted-foreground">{Math.ceil(evidence.fileSize / 1024)}KB</span>
                <select className="h-8 rounded border border-border bg-surface px-1 text-xs" value={evidence.category} onChange={(e) => void changeCategory(evidence, e.target.value)}>
                  {Object.entries(EVIDENCE_CATEGORY_LABELS).map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
                <button type="button" className="text-xs underline" onClick={() => void openEvidence(evidence.id, false).catch((e) => setError(errorMessage(e)))}>
                  보기
                </button>
                <button type="button" className="text-xs underline" onClick={() => void openEvidence(evidence.id, true).catch((e) => setError(errorMessage(e)))}>
                  다운로드
                </button>
                <button type="button" className="text-xs text-danger underline" onClick={() => detach(evidence)}>
                  연결 해제
                </button>
              </li>
            ))}
          </ul>
        ) : null}
      </Section>

      {duplicates && duplicates.length > 0 ? (
        <Notice tone="warn">
          <p className="font-medium">중복으로 의심되는 매입내역이 있습니다.</p>
          <ul className="mt-1 list-disc pl-4">
            {duplicates.map((d) => (
              <li key={d.ledgerId}>
                <a href={`/admin/purchases/${d.ledgerId}`} target="_blank" rel="noreferrer" className="underline">
                  {d.managementNo}
                </a>{" "}
                {d.purchaseDate} {d.supplierName} {won(d.purchaseAmount)} — {d.reasons.join(", ")}
              </li>
            ))}
          </ul>
          <div className="mt-2 flex gap-2">
            <button type="button" className={dangerButtonClass} disabled={busy} onClick={() => void save(true)}>
              중복 아님을 확인하고 저장
            </button>
            <button type="button" className={buttonClass} onClick={() => setDuplicates(null)}>
              취소
            </button>
          </div>
        </Notice>
      ) : null}

      {error ? <Notice tone="danger">{error}</Notice> : null}
      <div className="flex gap-2">
        <button type="submit" className={primaryButtonClass} disabled={busy}>
          {busy ? "저장 중..." : submitLabel ?? "저장"}
        </button>
      </div>
    </form>
  );
}

function isNullableSelect(key: keyof LedgerRequest): boolean {
  return ["paymentMethod", "statementKept", "transferKept", "evidenceType", "paymentDate"].includes(key);
}
