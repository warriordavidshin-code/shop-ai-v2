"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  adminJson,
  downloadFile,
  errorMessage,
  EVIDENCE_TYPE_LABELS,
  KEPT_LABELS,
  type LedgerRow,
  type Page,
  PAYMENT_METHOD_LABELS,
  query,
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
  Table,
} from "@/components/admin/ops/ui";

type Filters = {
  year: string;
  month: string;
  from: string;
  to: string;
  supplier: string;
  evidenceType: string;
  verification: string;
  keyword: string;
  sort: string;
};

const EMPTY: Filters = {
  year: "",
  month: "",
  from: "",
  to: "",
  supplier: "",
  evidenceType: "",
  verification: "",
  keyword: "",
  sort: "purchaseDate,desc",
};

const SORTS = [
  ["purchaseDate,desc", "매입일 최신순"],
  ["purchaseDate,asc", "매입일 오래된순"],
  ["managementNo,desc", "관리번호 역순"],
  ["supplierName,asc", "거래처명"],
  ["purchaseAmount,desc", "매입액 큰순"],
  ["paymentAmount,desc", "지급액 큰순"],
  ["createdAt,desc", "등록 최신순"],
] as const;

export function PurchaseLedgerList({ initial }: { initial?: Partial<Filters> }) {
  const [draft, setDraft] = useState<Filters>({ ...EMPTY, ...initial });
  const [filters, setFilters] = useState<Filters>({ ...EMPTY, ...initial });
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<LedgerRow> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [exportScope, setExportScope] = useState("search");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await adminJson<Page<LedgerRow>>(
        `/admin/purchases${query({ ...filters, page, size: 20 })}`,
      );
      setData(result);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    void load();
  }, [load]);

  function apply(e: React.FormEvent) {
    e.preventDefault();
    setPage(0);
    setFilters(draft);
  }

  async function remove(row: LedgerRow) {
    if (!window.confirm(`${row.managementNo} 매입내역을 삭제할까요? 삭제 이력은 감사 로그에 남고, 입고 대기 건은 함께 취소됩니다. 입고 확정된 건은 삭제할 수 없습니다.`)) return;
    try {
      await adminJson(`/admin/purchases/${row.id}`, { method: "DELETE" });
      setMessage(`${row.managementNo} 삭제됨`);
      void load();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  async function exportExcel() {
    setError(null);
    setMessage(null);
    const params: Record<string, string> = { scope: exportScope };
    if (exportScope === "year") {
      params.year = filters.year || String(new Date().getFullYear());
    } else if (exportScope === "month") {
      if (!filters.month) {
        setError("월별 내보내기는 검색 조건의 '월'을 먼저 선택해 주세요.");
        return;
      }
      params.month = filters.month;
    } else if (exportScope === "supplier") {
      if (!filters.supplier) {
        setError("거래처별 내보내기는 검색 조건의 '거래처'를 먼저 입력해 주세요.");
        return;
      }
      params.supplier = filters.supplier;
      if (filters.year) params.year = filters.year;
    } else if (exportScope === "search") {
      Object.assign(
        params,
        Object.fromEntries(Object.entries(filters).filter(([k, v]) => v && k !== "sort")),
      );
    }
    try {
      const { rows } = await downloadFile(`/admin/purchases/export${query(params)}`, "상품매입장부.xlsx");
      setMessage(rows === null ? "엑셀 파일을 내려받았습니다." : `엑셀 파일을 내려받았습니다. (${rows}건)`);
    } catch (e) {
      setError(errorMessage(e, "엑셀 내보내기에 실패했습니다."));
    }
  }

  const set = (key: keyof Filters) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setDraft((prev) => ({ ...prev, [key]: e.target.value }));

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="상품매입장부"
        description="엑셀 매입장부와 같은 17개 항목으로 관리합니다. 매입장부 등록만으로는 재고가 늘지 않으며, 입고 확정 시에만 재고가 반영됩니다."
        actions={
          <>
            <Link href="/admin/purchases/ai" className={buttonClass}>
              AI 영수증 분석
            </Link>
            <Link href="/admin/purchases/new" className={primaryButtonClass}>
              매입 등록
            </Link>
          </>
        }
      />

      <form onSubmit={apply} className="grid gap-2 rounded-xl border border-border bg-surface p-3 sm:grid-cols-4 lg:grid-cols-6">
        <input className={inputClass} placeholder="연도 (예: 2026)" inputMode="numeric" value={draft.year} onChange={set("year")} />
        <input className={inputClass} type="month" value={draft.month} onChange={set("month")} title="월" />
        <input className={inputClass} type="date" value={draft.from} onChange={set("from")} title="시작일" />
        <input className={inputClass} type="date" value={draft.to} onChange={set("to")} title="종료일" />
        <input className={inputClass} placeholder="거래처" value={draft.supplier} onChange={set("supplier")} />
        <input className={inputClass} placeholder="품목·관리번호·적요 검색" value={draft.keyword} onChange={set("keyword")} />
        <select className={inputClass} value={draft.evidenceType} onChange={set("evidenceType")}>
          <option value="">지출증빙 전체</option>
          {Object.entries(EVIDENCE_TYPE_LABELS).map(([k, v]) => (
            <option key={k} value={k}>
              {v}
            </option>
          ))}
        </select>
        <select className={inputClass} value={draft.verification} onChange={set("verification")}>
          <option value="">자료 확인 전체</option>
          <option value="NEEDS_REVIEW">자료 확인 필요</option>
          <option value="BASIC_COMPLETE">기본자료 보관</option>
        </select>
        <select className={inputClass} value={draft.sort} onChange={set("sort")}>
          {SORTS.map(([k, v]) => (
            <option key={k} value={k}>
              {v}
            </option>
          ))}
        </select>
        <button type="submit" className={primaryButtonClass}>
          검색
        </button>
        <button
          type="button"
          className={buttonClass}
          onClick={() => {
            setDraft(EMPTY);
            setFilters(EMPTY);
            setPage(0);
          }}
        >
          초기화
        </button>
      </form>

      <div className="flex flex-wrap items-center gap-2">
        <select className={inputClass} value={exportScope} onChange={(e) => setExportScope(e.target.value)}>
          <option value="search">현재 검색결과</option>
          <option value="all">전체</option>
          <option value="year">연도별 (검색 연도, 없으면 올해)</option>
          <option value="month">월별 (검색 월)</option>
          <option value="supplier">거래처별 (검색 거래처)</option>
        </select>
        <button type="button" className={buttonClass} onClick={() => void exportExcel()}>
          엑셀 다운로드
        </button>
        {data ? (
          <span className="text-sm text-muted-foreground">
            총 {data.totalElements.toLocaleString("ko-KR")}건 {loading ? "· 불러오는 중..." : ""}
          </span>
        ) : null}
      </div>

      {error ? <Notice tone="danger">{error}</Notice> : null}
      {message ? <Notice tone="ok">{message}</Notice> : null}

      <Table>
        <thead>
          <tr>
            <th>관리번호</th>
            <th>매입일</th>
            <th>거래처</th>
            <th>사업자번호</th>
            <th>품목</th>
            <th className="text-right">매입액</th>
            <th className="text-right">공급가액</th>
            <th className="text-right">세액</th>
            <th className="text-right">지급액</th>
            <th>지급일</th>
            <th>지급방법</th>
            <th>입금자/수취인</th>
            <th>이체 참조</th>
            <th className="text-right">차액</th>
            <th>명세표</th>
            <th>이체내역</th>
            <th>지출증빙</th>
            <th>자료 확인</th>
            <th>증빙</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {data?.content.length === 0 ? (
            <tr>
              <td colSpan={20} className="py-10 text-center text-muted-foreground">
                매입내역이 없습니다.
              </td>
            </tr>
          ) : null}
          {data?.content.map((row) => (
            <tr key={row.id}>
              <td>
                <Link href={`/admin/purchases/${row.id}`} className="font-medium underline-offset-2 hover:underline">
                  {row.managementNo}
                </Link>
                {row.entrySource === "AI" ? (
                  <span className="ml-1">
                    <Badge>AI</Badge>
                  </span>
                ) : null}
              </td>
              <td>{row.purchaseDate}</td>
              <td>{row.supplierName ?? "-"}</td>
              <td>{row.supplierBusinessNo ?? "-"}</td>
              <td className="max-w-[16rem] truncate" title={row.itemSummary ?? ""}>
                {row.itemSummary ?? "-"}
              </td>
              <td className="text-right tabular-nums">{won(row.purchaseAmount)}</td>
              <td className="text-right tabular-nums">{won(row.supplyAmount)}</td>
              <td className="text-right tabular-nums">{won(row.taxAmount)}</td>
              <td className="text-right tabular-nums">{won(row.paymentAmount)}</td>
              <td>{row.paymentDate ?? "-"}</td>
              <td>{row.paymentMethod ? PAYMENT_METHOD_LABELS[row.paymentMethod] : "-"}</td>
              <td>{row.recipientName ?? "-"}</td>
              <td>{row.transferReference ?? "-"}</td>
              <td className={`text-right tabular-nums ${row.differenceAmount ? "text-danger" : ""}`}>
                {won(row.differenceAmount)}
              </td>
              <td>{row.statementKept ? KEPT_LABELS[row.statementKept] : "-"}</td>
              <td>{row.transferKept ? KEPT_LABELS[row.transferKept] : "-"}</td>
              <td>{row.evidenceType ? EVIDENCE_TYPE_LABELS[row.evidenceType] : "-"}</td>
              <td>
                <Badge tone={row.verificationStatus === "BASIC_COMPLETE" ? "ok" : "warn"}>{row.verificationLabel}</Badge>
              </td>
              <td className="tabular-nums">{row.evidenceCount}</td>
              <td className="whitespace-nowrap">
                <Link href={`/admin/purchases/${row.id}`} className="mr-2 text-xs underline">
                  상세·수정
                </Link>
                <button type="button" className="text-xs text-danger underline" onClick={() => void remove(row)}>
                  삭제
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </Table>
      {data ? <Pager page={data.page} totalPages={data.totalPages} onPage={setPage} /> : null}
    </div>
  );
}
