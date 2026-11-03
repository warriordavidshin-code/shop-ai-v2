"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  adminJson,
  dateTime,
  errorMessage,
  type Evidence,
  EVIDENCE_AI_STATUS_LABELS,
  EVIDENCE_CATEGORY_LABELS,
  type Page,
  query,
  type SummaryResponse,
  won,
} from "@/features/admin/ops";
import { openEvidence } from "@/components/admin/ops/EvidencePreview";
import {
  BarChart,
  buttonClass,
  HBarChart,
  inputClass,
  Notice,
  PageHeader,
  Pager,
  Section,
  StatCard,
  Table,
} from "@/components/admin/ops/ui";

export function PurchaseSummaryClient() {
  const [year, setYear] = useState(new Date().getFullYear());
  const [data, setData] = useState<SummaryResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setError(null);
    adminJson<SummaryResponse>(`/admin/purchases/summary?year=${year}`)
      .then(setData)
      .catch((e) => setError(errorMessage(e)));
  }, [year]);

  const labels = data?.months.map((m) => `${m.month}월`) ?? [];
  return (
    <>
      <PageHeader
        title="월별 매입 현황"
        description="엑셀 매입장부의 월별 요약과 같은 기준입니다. 차액은 매입액 − 지급액입니다."
        actions={
          <div className="flex items-center gap-2">
            <button type="button" className={buttonClass} onClick={() => setYear((y) => y - 1)}>
              ◀
            </button>
            <span className="tabular-nums">{year}년</span>
            <button type="button" className={buttonClass} onClick={() => setYear((y) => y + 1)}>
              ▶
            </button>
          </div>
        }
      />
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {data ? (
        <>
          <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
            <StatCard label="매입액" value={won(data.total.purchaseAmount)} />
            <StatCard label="지급액" value={won(data.total.paymentAmount)} />
            <StatCard label="차액" value={won(data.total.differenceAmount)} tone={data.total.differenceAmount ? "warn" : undefined} />
            <StatCard label="매입 건수" value={`${data.total.purchaseCount}건`} />
            <StatCard label="자료 확인 필요" value={`${data.total.needsReviewCount}건`} tone={data.total.needsReviewCount ? "warn" : "ok"} href="/admin/purchases" />
            <StatCard label="미연결 증빙" value={`${data.unlinkedEvidenceCount}건`} tone={data.unlinkedEvidenceCount ? "warn" : undefined} href="/admin/purchases/evidence" />
          </div>
          <Section title="월별 매입액 · 지급액">
            <BarChart
              labels={labels}
              series={[
                { name: "매입액", values: data.months.map((m) => m.purchaseAmount) },
                { name: "지급액", values: data.months.map((m) => m.paymentAmount) },
              ]}
              lines={[{ name: "차액", values: data.months.map((m) => m.differenceAmount), color: "#c0392b" }]}
            />
          </Section>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <Section title="월별 증빙 보관 (건)">
              <BarChart
                labels={labels}
                series={[
                  { name: "매입 건수", values: data.months.map((m) => m.purchaseCount) },
                  { name: "명세표 보관", values: data.months.map((m) => m.statementKeptCount) },
                  { name: "이체내역 보관", values: data.months.map((m) => m.transferKeptCount) },
                ]}
              />
            </Section>
            <Section title="거래처별 매입액 (상위)">
              <HBarChart items={data.suppliers.slice(0, 10).map((s) => ({ name: s.supplierName, value: s.purchaseAmount }))} />
            </Section>
          </div>
          <Table>
            <thead>
              <tr>
                <th>월</th>
                <th className="text-right">매입액</th>
                <th className="text-right">지급액</th>
                <th className="text-right">차액</th>
                <th className="text-right">건수</th>
                <th className="text-right">명세표 보관</th>
                <th className="text-right">이체내역 보관</th>
                <th className="text-right">자료 확인 필요</th>
                <th className="text-right">증빙 누락</th>
              </tr>
            </thead>
            <tbody>
              {[...data.months, data.total].map((m, i) => (
                <tr key={i} className={i === data.months.length ? "font-semibold" : ""}>
                  <td>
                    {i === data.months.length ? (
                      "합계"
                    ) : (
                      <Link className="underline" href={`/admin/purchases?month=${year}-${String(m.month).padStart(2, "0")}`}>
                        {m.month}월
                      </Link>
                    )}
                  </td>
                  <td className="text-right tabular-nums">{won(m.purchaseAmount)}</td>
                  <td className="text-right tabular-nums">{won(m.paymentAmount)}</td>
                  <td className={`text-right tabular-nums ${m.differenceAmount ? "text-danger" : ""}`}>{won(m.differenceAmount)}</td>
                  <td className="text-right tabular-nums">{m.purchaseCount}</td>
                  <td className="text-right tabular-nums">{m.statementKeptCount}</td>
                  <td className="text-right tabular-nums">{m.transferKeptCount}</td>
                  <td className="text-right tabular-nums">{m.needsReviewCount}</td>
                  <td className="text-right tabular-nums">{m.missingEvidenceCount}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        </>
      ) : null}
    </>
  );
}

export function EvidenceManagerClient() {
  const [linked, setLinked] = useState("all");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<Evidence> | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setData(await adminJson<Page<Evidence>>(`/admin/purchases/evidence${query({ linked, page, size: 30 })}`));
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [linked, page]);

  useEffect(() => {
    void load();
  }, [load]);

  async function remove(evidence: Evidence) {
    if (!window.confirm(`${evidence.originalFileName} 파일을 삭제할까요? 삭제 이력은 감사 로그에 남습니다.`)) return;
    try {
      await adminJson(`/admin/purchases/evidence/${evidence.id}`, { method: "DELETE" });
      void load();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <>
      <PageHeader
        title="증빙 관리"
        description="업로드된 영수증·명세표·이체내역 원본입니다. 비공개 저장소에 있으며 관리자만 열람·다운로드할 수 있습니다."
        actions={
          <select className={inputClass} value={linked} onChange={(e) => { setLinked(e.target.value); setPage(0); }}>
            <option value="all">전체</option>
            <option value="linked">매입내역 연결됨</option>
            <option value="unlinked">미연결 (분석만 하고 저장 안 함)</option>
          </select>
        }
      />
      {error ? <Notice tone="danger">{error}</Notice> : null}
      <Table>
        <thead>
          <tr>
            <th>파일명</th>
            <th>분류</th>
            <th>매입내역</th>
            <th className="text-right">크기</th>
            <th>AI 분석</th>
            <th>업로드</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {data?.content.length === 0 ? (
            <tr>
              <td colSpan={7} className="py-8 text-center text-muted-foreground">
                증빙이 없습니다.
              </td>
            </tr>
          ) : null}
          {data?.content.map((e) => (
            <tr key={e.id}>
              <td className="max-w-[18rem] truncate" title={e.originalFileName}>
                {e.originalFileName}
              </td>
              <td>{EVIDENCE_CATEGORY_LABELS[e.category] ?? e.categoryLabel}</td>
              <td>
                {e.purchaseLedgerId ? (
                  <Link className="underline" href={`/admin/purchases/${e.purchaseLedgerId}`}>
                    {e.managementNo}
                  </Link>
                ) : (
                  <span className="text-muted-foreground">미연결</span>
                )}
              </td>
              <td className="text-right tabular-nums">{Math.ceil(e.fileSize / 1024)}KB</td>
              <td>{EVIDENCE_AI_STATUS_LABELS[e.aiAnalysisStatus] ?? e.aiAnalysisStatus}</td>
              <td>{dateTime(e.createdAt)}</td>
              <td className="whitespace-nowrap">
                <button type="button" className="mr-2 text-xs underline" onClick={() => void openEvidence(e.id, false).catch((err) => setError(errorMessage(err)))}>
                  보기
                </button>
                <button type="button" className="mr-2 text-xs underline" onClick={() => void openEvidence(e.id, true).catch((err) => setError(errorMessage(err)))}>
                  다운로드
                </button>
                <button type="button" className="text-xs text-danger underline" onClick={() => void remove(e)}>
                  삭제
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </Table>
      {data ? <Pager page={data.page} totalPages={data.totalPages} onPage={setPage} /> : null}
    </>
  );
}
