"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  adminJson,
  AI_MODE_LABELS,
  type AiStatus,
  BILLING_LABELS,
  errorMessage,
  isPaidApprovalRequired,
  PROVIDER_LABELS,
  type LedgerDetail,
  MATCH_STATUS_LABELS,
  postForm,
  RECEIPT_STATUS_LABELS,
  type ReceiptAnalysis,
  dateTime,
  won,
} from "@/features/admin/ops";
import { EvidencePreview } from "@/components/admin/ops/EvidencePreview";
import { emptyLedgerRequest, PurchaseLedgerForm, requestFromDetail } from "@/components/admin/ops/PurchaseLedgerForm";
import {
  Badge,
  buttonClass,
  Notice,
  PageHeader,
  primaryButtonClass,
  Section,
  Table,
  WarningList,
} from "@/components/admin/ops/ui";

export function PurchaseNewClient() {
  const router = useRouter();
  return (
    <>
      <PageHeader
        title="매입 등록"
        description="직접 입력합니다. 영수증·명세표 사진이 있으면 AI 영수증 분석으로 초안을 만든 뒤 검토해서 저장할 수 있습니다."
        actions={
          <Link href="/admin/purchases/ai" className={buttonClass}>
            AI 영수증 분석으로 등록
          </Link>
        }
      />
      <PurchaseLedgerForm
        initial={emptyLedgerRequest()}
        onSaved={(detail) => router.push(`/admin/purchases/${detail.ledger.id}`)}
      />
    </>
  );
}

export function PurchaseDetailClient({ ledgerId }: { ledgerId: number }) {
  const [detail, setDetail] = useState<LedgerDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const load = useCallback(async () => {
    try {
      setDetail(await adminJson<LedgerDetail>(`/admin/purchases/${ledgerId}`));
    } catch (e) {
      setError(errorMessage(e, "매입내역을 불러오지 못했습니다."));
    }
  }, [ledgerId]);

  useEffect(() => {
    void load();
  }, [load]);

  if (error) return <Notice tone="danger">{error}</Notice>;
  if (!detail) return <p className="text-sm text-muted-foreground">불러오는 중...</p>;

  const l = detail.ledger;
  const needsMatching = detail.items.some((item) => item.matchStatus !== "MATCHED" && item.matchStatus !== "IGNORED");
  const receivable = detail.items.some((item) => item.matchStatus === "MATCHED" && (item.remainingQuantity ?? 0) > 0);

  return (
    <>
      <PageHeader
        title={`매입내역 ${l.managementNo}`}
        description={
          <>
            {l.purchaseDate} · {l.supplierName ?? "거래처 미입력"} · 등록 {dateTime(l.createdAt)}
            {l.entrySource === "AI" ? " · AI 분석 초안에서 저장됨" : ""}
          </>
        }
        actions={
          <>
            <Link href="/admin/purchases" className={buttonClass}>
              목록
            </Link>
            {needsMatching ? (
              <Link href={`/admin/purchases/matching?ledgerId=${l.id}`} className={buttonClass}>
                상품 매칭
              </Link>
            ) : null}
            <Link
              href={`/admin/inventory/receipts?ledgerId=${l.id}`}
              className={receivable ? primaryButtonClass : buttonClass}
            >
              입고 등록
            </Link>
          </>
        }
      />
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <Badge tone={l.verificationStatus === "BASIC_COMPLETE" ? "ok" : "warn"}>{l.verificationLabel}</Badge>
        {l.missingFields.length > 0 ? (
          <span className="text-muted-foreground">누락: {l.missingFields.join(", ")}</span>
        ) : null}
        <span>
          차액 <strong className={l.differenceAmount ? "text-danger" : ""}>{won(l.differenceAmount)}</strong>
        </span>
      </div>
      {saved ? <Notice tone="ok">저장했습니다.</Notice> : null}
      <Notice>
        매입장부 저장은 재고에 영향을 주지 않습니다. 실제 입고 수량을 확인한 뒤 &lsquo;입고 등록 → 입고 확정&rsquo;을
        해야 재고와 원가가 반영됩니다.
      </Notice>

      <Section title="품목별 매칭 · 입고 현황">
        <Table>
          <thead>
            <tr>
              <th>#</th>
              <th>상품명</th>
              <th>옵션</th>
              <th className="text-right">수량</th>
              <th className="text-right">단가</th>
              <th>매칭</th>
              <th>매칭 상품</th>
              <th className="text-right">입고</th>
              <th className="text-right">미입고</th>
            </tr>
          </thead>
          <tbody>
            {detail.items.length === 0 ? (
              <tr>
                <td colSpan={9} className="py-6 text-center text-muted-foreground">
                  품목이 없습니다. 품목을 추가해야 입고를 등록할 수 있습니다.
                </td>
              </tr>
            ) : null}
            {detail.items.map((item) => (
              <tr key={item.id ?? item.lineNo}>
                <td>{item.lineNo}</td>
                <td>{item.productName}</td>
                <td>{[item.color, item.size].filter(Boolean).join(" / ") || "-"}</td>
                <td className="text-right tabular-nums">{item.quantity ?? "-"}</td>
                <td className="text-right tabular-nums">{won(item.unitPrice)}</td>
                <td>
                  <Badge tone={item.matchStatus === "MATCHED" ? "ok" : item.matchStatus === "IGNORED" ? "muted" : "warn"}>
                    {MATCH_STATUS_LABELS[item.matchStatus ?? "UNMATCHED"] ?? item.matchStatus}
                  </Badge>
                </td>
                <td>
                  {item.matchedProductId ? (
                    <Link href={`/admin/products/${item.matchedProductId}/edit`} className="underline">
                      {item.matchedProductName} {item.matchedOptionName ? `(${item.matchedOptionName})` : ""}
                    </Link>
                  ) : (
                    <span className="text-muted-foreground">{item.matchNote ?? "-"}</span>
                  )}
                </td>
                <td className="text-right tabular-nums">{item.receivedQuantity ?? 0}</td>
                <td className="text-right tabular-nums">{item.remainingQuantity ?? 0}</td>
              </tr>
            ))}
          </tbody>
        </Table>
        {detail.receipts.length > 0 ? (
          <ul className="flex flex-wrap gap-2 text-sm">
            {detail.receipts.map((r) => (
              <li key={r.id}>
                <Link href={`/admin/inventory/receipts?receiptId=${r.id}`} className="underline">
                  {r.receiptNo}
                </Link>{" "}
                <Badge tone={r.status === "CONFIRMED" ? "ok" : r.status === "PENDING" ? "warn" : "muted"}>
                  {RECEIPT_STATUS_LABELS[r.status] ?? r.status}
                </Badge>
              </li>
            ))}
          </ul>
        ) : null}
      </Section>

      <PurchaseLedgerForm
        key={l.version}
        ledgerId={l.id}
        initial={requestFromDetail(detail)}
        initialEvidences={detail.evidences}
        onSaved={(next) => {
          setDetail(next);
          setSaved(true);
        }}
      />
    </>
  );
}

function camel(field: string): string {
  return field.replace(/_([a-z])/g, (_, c: string) => c.toUpperCase());
}

const REVIEW_FIELD_LABELS: Record<string, string> = {
  all: "전체",
  supplierName: "거래처",
  supplierBusinessNo: "사업자번호",
  itemSummary: "품목 요약",
  purchaseDate: "매입일",
  purchaseAmount: "매입액",
  supplyAmount: "공급가액",
  taxAmount: "세액",
  paymentAmount: "지급액",
  paymentDate: "지급일",
  paymentMethod: "지급방법",
  recipientName: "입금자/수취인",
  statementKept: "거래명세표 보관",
  transferKept: "이체내역 보관",
  transferReference: "이체 참조",
  evidenceType: "지출증빙",
  items: "품목",
};

function reviewFieldLabel(field: string): string {
  const item = /^items\[(\d+)\]/.exec(field);
  if (item) return `품목 ${item[1]}`;
  const key = camel(field);
  return REVIEW_FIELD_LABELS[key] ?? key;
}

export function PurchaseAiClient() {
  const router = useRouter();
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [files, setFiles] = useState<File[]>([]);
  const [result, setResult] = useState<ReceiptAnalysis | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState(0);
  const [paidPrompt, setPaidPrompt] = useState<string | null>(null);

  useEffect(() => {
    adminJson<AiStatus>("/admin/ai/analysis-status")
      .then(setStatus)
      .catch(() => setStatus(null));
  }, []);

  async function analyze(forceRefresh: boolean, allowPaid = false) {
    if (files.length === 0) {
      setError("분석할 파일을 선택해 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    setPaidPrompt(null);
    const body = new FormData();
    files.forEach((file) => body.append("files", file));
    if (forceRefresh) body.append("forceRefresh", "true");
    if (allowPaid) body.append("allowPaid", "true");
    try {
      const analysis = await postForm<ReceiptAnalysis>("/admin/purchases/ai/analyze", body);
      setResult(analysis);
      setSelected(0);
    } catch (e) {
      if (isPaidApprovalRequired(e)) {
        setPaidPrompt(errorMessage(e));
      } else {
        setError(errorMessage(e, "AI 분석에 실패했습니다. 직접 입력으로 등록할 수 있습니다."));
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

  const reviewNotes = useMemo(() => {
    const notes: Record<string, string[]> = {};
    if (!result) return notes;
    for (const field of result.reviewFields) {
      const key = camel(field.field);
      (notes[key] ??= []).push(field.reason);
    }
    return notes;
  }, [result]);

  const unavailable = status !== null && (!status.receiptEnabled || !status.available);

  return (
    <>
      <PageHeader
        title="AI 영수증 · 명세표 분석"
        description="거래명세표, 영수증, 이체내역, 세금계산서를 함께 올리면 매입장부 초안을 만듭니다. 관리자가 검토하고 저장하기 전에는 아무것도 등록되지 않습니다."
        actions={
          <Link href="/admin/purchases/new" className={buttonClass}>
            직접 입력
          </Link>
        }
      />

      {status ? (
        unavailable ? (
          <Notice tone="warn">
            AI 영수증 분석을 사용할 수 없습니다.
            {!status.receiptEnabled ? " (AI_RECEIPT_ENABLED=false)" : ""}
            {status.mode === "MANUAL" ? " 현재 AI 모드가 '수동 입력'입니다." : ""} 직접 입력으로 등록해 주세요.
          </Notice>
        ) : status.mockMode ? (
          <Notice tone="warn">
            현재 <strong>Mock 모드</strong>입니다. 분석 결과는 실제 AI가 아닌 테스트 데이터이며 [MOCK]으로 표시됩니다.
          </Notice>
        ) : (
          <Notice>
            {status.mode ? `${AI_MODE_LABELS[status.mode] ?? status.mode}. ` : ""}
            로컬 OCR과 규칙으로 먼저 읽고, 불확실할 때만 무료 AI(Gemini)를 사용합니다. 유료 AI는 승인 없이 호출하지 않습니다.
            원본은 비공개 저장소에 보관되며, 확인할 수 없는 값은 비워 둡니다.
          </Notice>
        )
      ) : null}

      <Section title="1. 파일 선택">
        <div className="flex flex-wrap items-center gap-2">
          <label className={`${buttonClass} cursor-pointer`}>
            파일 선택 (JPG·PNG·WEBP·PDF, 여러 개)
            <input
              type="file"
              multiple
              accept="image/jpeg,image/png,image/webp,application/pdf"
              className="hidden"
              onChange={(e) => {
                setFiles(Array.from(e.target.files ?? []));
                setResult(null);
                e.target.value = "";
              }}
            />
          </label>
          <button type="button" className={primaryButtonClass} disabled={busy || files.length === 0 || unavailable} onClick={() => void analyze(false)}>
            {busy ? "분석 중..." : "AI 분석"}
          </button>
          {result ? (
            <button type="button" className={buttonClass} disabled={busy} onClick={() => void analyze(true)}>
              다시 분석 (캐시 무시)
            </button>
          ) : null}
        </div>
        {files.length > 0 ? (
          <ul className="text-sm text-muted-foreground">
            {files.map((file) => (
              <li key={file.name + file.size}>
                {file.name} ({Math.ceil(file.size / 1024)}KB)
              </li>
            ))}
          </ul>
        ) : null}
      </Section>

      {error ? <Notice tone="danger">{error}</Notice> : null}
      {paidPrompt ? (
        <Notice tone="warn">
          <p>{paidPrompt}</p>
          <div className="mt-2 flex flex-wrap gap-2">
            <button type="button" className={primaryButtonClass} disabled={busy} onClick={approvePaid}>
              유료 AI(OpenAI) 1회 사용 승인
            </button>
            <Link href="/admin/purchases/new" className={buttonClass}>
              직접 입력
            </Link>
          </div>
        </Notice>
      ) : null}

      {result ? (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
          <div className="flex flex-col gap-3 lg:sticky lg:top-4 lg:self-start">
            <Section title="원본">
              <div className="flex flex-wrap gap-1">
                {result.evidences.map((evidence, i) => (
                  <button
                    key={evidence.id}
                    type="button"
                    className={`rounded px-2 py-1 text-xs ${i === selected ? "bg-brand text-white" : "border border-border"}`}
                    onClick={() => setSelected(i)}
                  >
                    {result.documents.find((d) => d.evidenceId === evidence.id)?.documentLabel ?? `파일 ${i + 1}`}
                  </button>
                ))}
              </div>
              {result.evidences[selected] ? <EvidencePreview evidence={result.evidences[selected]} /> : null}
            </Section>
          </div>
          <div className="flex flex-col gap-3">
            <Section title="2. AI 분석 결과">
              <div className="flex flex-wrap gap-2 text-xs">
                <Badge tone={result.mock ? "warn" : "ok"}>
                  {result.mock ? "MOCK 결과" : `${PROVIDER_LABELS[result.provider] ?? result.provider} · ${result.model ?? "-"}`}
                </Badge>
                {result.billingType ? (
                  <Badge tone={result.billingType === "PAID" ? "warn" : "muted"}>{BILLING_LABELS[result.billingType] ?? result.billingType}</Badge>
                ) : null}
                {result.cached ? <Badge>캐시된 결과</Badge> : null}
                {result.estimatedCostUsd !== null ? <Badge>예상 비용 ${Number(result.estimatedCostUsd).toFixed(4)}</Badge> : null}
              </div>
              <WarningList items={result.notices} tone="info" />
              {result.paidApprovalAvailable ? (
                <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                  결과가 불확실하면 유료 AI로 다시 분석할 수 있습니다.
                  <button type="button" className={buttonClass} disabled={busy} onClick={approvePaid}>
                    유료 AI로 재분석 (승인)
                  </button>
                </div>
              ) : null}
              <Table>
                <thead>
                  <tr>
                    <th>파일</th>
                    <th>문서 종류</th>
                    <th className="text-right">신뢰도</th>
                  </tr>
                </thead>
                <tbody>
                  {result.documents.map((doc) => (
                    <tr key={doc.fileIndex}>
                      <td className="max-w-[12rem] truncate">{doc.fileName}</td>
                      <td>{doc.documentLabel}</td>
                      <td className="text-right tabular-nums">
                        {doc.confidence === null ? "-" : `${Math.round(Number(doc.confidence) * 100)}%`}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
              <WarningList items={result.warnings} />
              {result.reviewFields.length > 0 ? (
                <Notice tone="warn">
                  <p className="font-medium">확인이 필요한 항목</p>
                  <ul className="list-disc pl-4">
                    {Array.from(new Set(result.reviewFields.map((f) => `${reviewFieldLabel(f.field)}: ${f.reason}`))).map((line) => (
                      <li key={line}>{line}</li>
                    ))}
                  </ul>
                </Notice>
              ) : null}
              {result.duplicates.length > 0 ? (
                <Notice tone="danger">
                  중복 의심:{" "}
                  {result.duplicates.map((d) => (
                    <a key={d.ledgerId} href={`/admin/purchases/${d.ledgerId}`} target="_blank" rel="noreferrer" className="mr-2 underline">
                      {d.managementNo} ({d.reasons.join(", ")})
                    </a>
                  ))}
                </Notice>
              ) : null}
            </Section>
            <p className="text-sm font-semibold">3. 검토 후 저장 (노란 칸은 확인이 필요한 값)</p>
            <PurchaseLedgerForm
              key={result.analysisId ?? "draft"}
              initial={result.draft}
              initialEvidences={result.evidences}
              reviewNotes={reviewNotes}
              submitLabel="검토 완료 · 매입장부에 저장"
              onSaved={(detail) => router.push(`/admin/purchases/${detail.ledger.id}`)}
            />
          </div>
        </div>
      ) : null}
    </>
  );
}
