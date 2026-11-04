"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  adminJson,
  type AiAnalysisRow,
  type AiStatus,
  type AiUsage,
  BILLING_LABELS,
  dateTime,
  errorMessage,
  num,
  type Page,
  postJson,
  query,
  type Recommendation,
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
  WarningList,
} from "@/components/admin/ops/ui";

const TYPE_LABELS: Record<string, string> = {
  RECEIPT: "영수증·명세표",
  PRODUCT: "상품 이미지",
  PURCHASE_RECOMMENDATION: "발주 추천",
};

export function AiOperationsClient() {
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [usage, setUsage] = useState<AiUsage | null>(null);
  const [days, setDays] = useState(30);
  const [type, setType] = useState("");
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<Page<AiAnalysisRow> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    adminJson<AiStatus>("/admin/ai/analysis-status").then(setStatus).catch((e) => setError(errorMessage(e)));
  }, []);

  useEffect(() => {
    adminJson<AiUsage>(`/admin/ai/usage?days=${days}`).then(setUsage).catch((e) => setError(errorMessage(e)));
  }, [days]);

  useEffect(() => {
    adminJson<Page<AiAnalysisRow>>(`/admin/ai/analyses${query({ type, page, size: 30 })}`)
      .then(setRows)
      .catch((e) => setError(errorMessage(e)));
  }, [type, page]);

  return (
    <>
      <PageHeader
        title="AI 분석 이력 · 사용량"
        description="AI 호출 기록입니다. API 키는 서버 환경변수로만 관리되며 화면이나 DB에 저장되지 않습니다. 원본 이미지·개인정보는 이력에 남기지 않습니다."
      />
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {status ? (
        <div className="grid gap-3 sm:grid-cols-4">
          <StatCard
            label="상태"
            value={status.available ? (status.mockMode ? "Mock 모드" : "사용 가능") : "사용 불가"}
            tone={status.available ? (status.mockMode ? "warn" : "ok") : "danger"}
            sub={`${status.mode ?? ""} · Gemini 키 ${status.geminiKeyConfigured ? "설정됨" : "미설정"} · OpenAI 키 ${status.apiKeyConfigured ? "설정됨" : "미설정"}`}
          />
          <StatCard label="모델" value={status.model ?? "-"} sub={status.provider ?? undefined} />
          <StatCard label="영수증 분석" value={status.receiptEnabled ? "켜짐" : "꺼짐"} tone={status.receiptEnabled ? "ok" : undefined} />
          <StatCard label="상품 분석" value={status.productEnabled ? "켜짐" : "꺼짐"} tone={status.productEnabled ? "ok" : undefined} />
        </div>
      ) : null}
      <Section
        title="사용량"
        actions={
          <select className={inputClass} value={days} onChange={(e) => setDays(Number(e.target.value))}>
            <option value={7}>최근 7일</option>
            <option value={30}>최근 30일</option>
            <option value={90}>최근 90일</option>
          </select>
        }
      >
        {usage ? (
          <div className="grid gap-3 sm:grid-cols-5">
            <StatCard label="외부 AI 호출" value={`${num(usage.calls)}회`} />
            <StatCard label="실패" value={`${num(usage.failures)}회`} tone={usage.failures ? "warn" : undefined} />
            <StatCard label="입력 토큰" value={num(usage.inputTokens)} />
            <StatCard label="출력 토큰" value={num(usage.outputTokens)} />
            <StatCard label="예상 비용" value={usage.estimatedCostUsd === null ? "-" : `$${Number(usage.estimatedCostUsd).toFixed(4)}`} sub={usage.note ?? undefined} />
          </div>
        ) : null}
      </Section>
      <Section
        title="분석 이력"
        actions={
          <select className={inputClass} value={type} onChange={(e) => { setType(e.target.value); setPage(0); }}>
            <option value="">전체</option>
            {Object.entries(TYPE_LABELS).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        }
      >
        <Table>
          <thead>
            <tr>
              <th>일시</th>
              <th>유형</th>
              <th>제공자 / 모델</th>
              <th>상태</th>
              <th className="text-right">파일</th>
              <th className="text-right">토큰 (입/출)</th>
              <th className="text-right">비용</th>
              <th className="text-right">소요</th>
              <th>대상</th>
              <th>오류</th>
            </tr>
          </thead>
          <tbody>
            {rows?.content.length === 0 ? (
              <tr>
                <td colSpan={10} className="py-6 text-center text-muted-foreground">
                  이력이 없습니다.
                </td>
              </tr>
            ) : null}
            {rows?.content.map((r) => (
              <tr key={r.id}>
                <td className="whitespace-nowrap">{dateTime(r.createdAt)}</td>
                <td>{TYPE_LABELS[r.type] ?? r.type}</td>
                <td>
                  {r.provider === "MOCK" ? <Badge tone="warn">MOCK</Badge> : r.provider} {r.model ?? ""}{" "}
                  {r.billingType ? <Badge tone={r.billingType === "PAID" ? "warn" : "muted"}>{BILLING_LABELS[r.billingType] ?? r.billingType}</Badge> : null}
                </td>
                <td>
                  <Badge tone={r.status === "SUCCEEDED" ? "ok" : r.status === "FAILED" ? "danger" : "muted"}>{r.status === "SUCCEEDED" ? "성공" : r.status === "FAILED" ? "실패" : r.status}</Badge>
                </td>
                <td className="text-right tabular-nums">{r.fileCount}</td>
                <td className="text-right tabular-nums">
                  {r.inputTokens ?? "-"} / {r.outputTokens ?? "-"}
                </td>
                <td className="text-right tabular-nums">{r.estimatedCostUsd === null ? "-" : `$${Number(r.estimatedCostUsd).toFixed(4)}`}</td>
                <td className="text-right tabular-nums">{r.durationMs === null ? "-" : `${(r.durationMs / 1000).toFixed(1)}s`}</td>
                <td className="text-xs">
                  {r.targetType === "PURCHASE_LEDGER" && r.targetId ? (
                    <Link href={`/admin/purchases/${r.targetId}`} className="underline">
                      매입 #{r.targetId}
                    </Link>
                  ) : r.targetType ? (
                    `${r.targetType} #${r.targetId ?? "-"}`
                  ) : (
                    "-"
                  )}
                </td>
                <td className="max-w-[14rem] truncate text-xs text-danger" title={r.errorMessage ?? ""}>
                  {r.errorCode ?? ""} {r.errorMessage ?? ""}
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
        {rows ? <Pager page={rows.page} totalPages={rows.totalPages} onPage={setPage} /> : null}
      </Section>
    </>
  );
}

const REC_STATUS: Record<string, string> = { OPEN: "검토 대기", ACCEPTED: "채택", DISMISSED: "보류" };

export function RecommendationsClient() {
  const [status, setStatus] = useState("OPEN");
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<Page<Recommendation> | null>(null);
  const [useAi, setUseAi] = useState(true);
  const [summary, setSummary] = useState<{ provider: string; aiUsed: boolean; summary: string; warnings: string[] } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setRows(await adminJson<Page<Recommendation>>(`/admin/ai/purchase-recommendations${query({ status, page, size: 30 })}`));
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [status, page]);

  useEffect(() => {
    void load();
  }, [load]);

  async function generate() {
    setBusy(true);
    setError(null);
    try {
      const result = await postJson<{ provider: string; aiUsed: boolean; summary: string; warnings: string[] }>(
        "/admin/ai/purchase-recommendations",
        { useAi },
      );
      setSummary(result);
      setStatus("OPEN");
      setPage(0);
      void load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function decide(id: number, decision: "ACCEPTED" | "DISMISSED") {
    try {
      await postJson(`/admin/ai/purchase-recommendations/${id}/decision`, { decision });
      void load();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <>
      <PageHeader
        title="AI 발주 추천"
        description="판매 속도, 품절 이력, 마진, 시즌, 거래처 리드타임으로 추천 수량을 계산하고, AI는 추천 사유 설명만 작성합니다. 채택해도 발주는 자동으로 실행되지 않습니다."
        actions={
          <>
            <label className="flex items-center gap-1 text-sm">
              <input type="checkbox" checked={useAi} onChange={(e) => setUseAi(e.target.checked)} />
              AI 설명 사용
            </label>
            <button type="button" className={primaryButtonClass} disabled={busy} onClick={() => void generate()}>
              {busy ? "분석 중..." : "추천 생성"}
            </button>
          </>
        }
      />
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {summary ? (
        <Notice tone={summary.provider === "MOCK" ? "warn" : "info"}>
          {summary.provider === "MOCK" ? "[MOCK] " : ""}
          {summary.aiUsed ? "AI가 사유를 작성했습니다. " : "규칙 기반 사유입니다. "}
          {summary.summary}
        </Notice>
      ) : null}
      <WarningList items={summary?.warnings} />
      <div className="flex gap-2">
        {Object.entries(REC_STATUS).map(([k, v]) => (
          <button key={k} type="button" className={status === k ? primaryButtonClass : buttonClass} onClick={() => { setStatus(k); setPage(0); }}>
            {v}
          </button>
        ))}
      </div>
      <Table>
        <thead>
          <tr>
            <th>우선순위</th>
            <th>유형</th>
            <th>상품 / 옵션</th>
            <th className="text-right">추천 수량</th>
            <th>사유</th>
            <th>작성</th>
            <th>생성일</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {rows?.content.length === 0 ? (
            <tr>
              <td colSpan={8} className="py-6 text-center text-muted-foreground">
                추천이 없습니다.
              </td>
            </tr>
          ) : null}
          {rows?.content.map((r) => (
            <tr key={r.id}>
              <td className="tabular-nums">{r.priority}</td>
              <td>{r.type === "PURCHASE" ? "발주" : r.type === "SLOW_MOVING" ? "장기 재고" : r.type}</td>
              <td>
                {r.productId ? (
                  <Link href={`/admin/products/${r.productId}/edit`} className="underline">
                    {r.productName}
                  </Link>
                ) : (
                  r.productName ?? "-"
                )}{" "}
                <span className="text-muted-foreground">{r.optionName ?? ""}</span>
              </td>
              <td className="text-right font-semibold tabular-nums">{r.suggestedQuantity ?? "-"}</td>
              <td className="max-w-[28rem] whitespace-pre-line text-xs">{r.reason}</td>
              <td>{r.provider === "MOCK" ? <Badge tone="warn">MOCK</Badge> : r.provider === "RULE" ? <Badge>규칙</Badge> : <Badge tone="ok">AI</Badge>}</td>
              <td className="whitespace-nowrap">{dateTime(r.createdAt)}</td>
              <td className="whitespace-nowrap">
                {r.status === "OPEN" ? (
                  <>
                    <button type="button" className="mr-2 text-xs underline" onClick={() => void decide(r.id, "ACCEPTED")}>
                      채택
                    </button>
                    <button type="button" className="text-xs underline" onClick={() => void decide(r.id, "DISMISSED")}>
                      보류
                    </button>
                  </>
                ) : (
                  <span className="text-xs text-muted-foreground">
                    {REC_STATUS[r.status] ?? r.status} {dateTime(r.decidedAt)}
                  </span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </Table>
      {rows ? <Pager page={rows.page} totalPages={rows.totalPages} onPage={setPage} /> : null}
    </>
  );
}
