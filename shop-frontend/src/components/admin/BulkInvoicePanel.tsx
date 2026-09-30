"use client";

import { useRef, useState } from "react";
import { ApiError } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import {
  bulkRegisterInvoices,
  type AdminOrderRow,
  type BulkInvoiceResult,
  type DeliveryCompany,
} from "@/features/admin/shipping";
import { decodeCsv, parseInvoiceCsv, type InvoiceRow } from "@/features/shipping/csv";

type Props = {
  companies: DeliveryCompany[];
  selected: AdminOrderRow[];
  onClearSelection: () => void;
  onDone: () => void;
};

const inputClass = "h-9 rounded-lg border border-border bg-surface px-2 text-sm";

/** 송장 일괄 등록: checked orders from the list, or an uploaded CSV (orderNumber, deliveryCompany, trackingNumber). */
export function BulkInvoicePanel({ companies, selected, onClearSelection, onDone }: Props) {
  const [mode, setMode] = useState<"selected" | "csv">("selected");
  const [company, setCompany] = useState("");
  const [trackingByOrder, setTrackingByOrder] = useState<Record<string, string>>({});
  const [csvRows, setCsvRows] = useState<InvoiceRow[]>([]);
  const [csvErrors, setCsvErrors] = useState<string[]>([]);
  const [fileName, setFileName] = useState<string | null>(null);
  const [result, setResult] = useState<BulkInvoiceResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const enabled = companies.filter((c) => c.enabled);
  const defaultCompany = company || enabled[0]?.code || "";

  async function onFile(file: File | undefined) {
    setResult(null);
    setError(null);
    if (!file) return;
    setFileName(file.name);
    const text = decodeCsv(await file.arrayBuffer());
    const parsed = parseInvoiceCsv(text);
    setCsvRows(parsed.rows);
    setCsvErrors(parsed.errors);
  }

  function selectedItems(): InvoiceRow[] {
    return selected
      .map((row) => ({
        orderNumber: row.orderNo,
        deliveryCompany: defaultCompany,
        trackingNumber: (trackingByOrder[row.orderNo] ?? "").replace(/[\s-]/g, ""),
      }))
      .filter((item) => item.trackingNumber !== "");
  }

  async function submit(items: InvoiceRow[]) {
    if (items.length === 0) {
      setError(mode === "csv" ? "등록할 행이 없습니다." : "송장번호를 입력해 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const res = await bulkRegisterInvoices(items);
      setResult(res);
      if (mode === "selected") {
        setTrackingByOrder({});
        onClearSelection();
      } else {
        setCsvRows([]);
        setFileName(null);
        if (fileRef.current) fileRef.current.value = "";
      }
      onDone();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "일괄 등록에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-base font-semibold">송장 일괄 등록</h2>
        <div className="flex gap-1">
          {(
            [
              ["selected", `선택 주문 (${selected.length})`],
              ["csv", "CSV/엑셀 업로드"],
            ] as const
          ).map(([value, label]) => (
            <button
              key={value}
              type="button"
              onClick={() => {
                setMode(value);
                setError(null);
              }}
              className={cn(
                "rounded-md px-3 py-1.5 text-xs",
                mode === value ? "bg-brand text-white" : "border border-border text-muted-foreground hover:bg-surface-soft",
              )}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {mode === "selected" ? (
        selected.length === 0 ? (
          <p className="text-muted-foreground">목록에서 송장을 등록할 주문을 체크해 주세요.</p>
        ) : (
          <>
            <label className="flex items-center gap-2">
              <span className="text-muted-foreground">택배사</span>
              <select className={inputClass} value={defaultCompany} onChange={(e) => setCompany(e.target.value)}>
                {enabled.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.companyName}
                  </option>
                ))}
              </select>
            </label>
            <ul className="flex flex-col gap-2">
              {selected.map((row) => (
                <li key={row.orderNo} className="flex flex-wrap items-center gap-2">
                  <span className="w-48 truncate tabular-nums">{row.orderNo}</span>
                  <span className="w-24 truncate text-muted-foreground">{row.memberName ?? row.receiverName ?? "-"}</span>
                  <input
                    className={cn(inputClass, "w-56")}
                    placeholder={row.trackingNumber ? `현재 ${row.trackingNumber}` : "송장번호"}
                    inputMode="numeric"
                    value={trackingByOrder[row.orderNo] ?? ""}
                    onChange={(e) => setTrackingByOrder((prev) => ({ ...prev, [row.orderNo]: e.target.value }))}
                  />
                </li>
              ))}
            </ul>
            <div>
              <button
                type="button"
                disabled={busy}
                onClick={() => void submit(selectedItems())}
                className="h-9 rounded-lg bg-brand px-4 text-xs font-medium text-white hover:bg-brand-hover disabled:opacity-60"
              >
                {busy ? "등록 중..." : "선택 주문 송장 등록"}
              </button>
            </div>
          </>
        )
      ) : (
        <>
          <p className="text-xs text-muted-foreground">
            열 순서: 주문번호, 택배사, 송장번호 (첫 행 헤더 허용 · 택배사는 코드(CJ) 또는 이름(CJ대한통운)). 엑셀은 “CSV UTF-8”
            또는 “CSV(쉼표로 분리)”로 저장해 업로드해 주세요.
          </p>
          <input
            ref={fileRef}
            type="file"
            accept=".csv,.txt,text/csv"
            onChange={(e) => void onFile(e.target.files?.[0])}
            className="text-xs"
          />
          {fileName ? (
            <p className="text-xs text-muted-foreground">
              {fileName} · 등록 대상 {csvRows.length}건{csvErrors.length ? ` · 제외 ${csvErrors.length}건` : ""}
            </p>
          ) : null}
          {csvErrors.length > 0 ? (
            <ul className="text-xs text-danger">
              {csvErrors.slice(0, 10).map((msg) => (
                <li key={msg}>{msg}</li>
              ))}
            </ul>
          ) : null}
          {csvRows.length > 0 ? (
            <>
              <div className="max-h-64 overflow-auto rounded-lg border border-border">
                <table className="min-w-full text-left text-xs">
                  <thead className="bg-surface-soft text-muted-foreground">
                    <tr>
                      <th className="px-3 py-2">#</th>
                      <th className="px-3 py-2">주문번호</th>
                      <th className="px-3 py-2">택배사</th>
                      <th className="px-3 py-2">송장번호</th>
                    </tr>
                  </thead>
                  <tbody>
                    {csvRows.map((row, i) => (
                      <tr key={`${row.orderNumber}-${i}`} className="border-t border-border">
                        <td className="px-3 py-1.5 tabular-nums">{i + 1}</td>
                        <td className="px-3 py-1.5 tabular-nums">{row.orderNumber}</td>
                        <td className="px-3 py-1.5">{row.deliveryCompany}</td>
                        <td className="px-3 py-1.5 tabular-nums">{row.trackingNumber}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => void submit(csvRows)}
                  className="h-9 rounded-lg bg-brand px-4 text-xs font-medium text-white hover:bg-brand-hover disabled:opacity-60"
                >
                  {busy ? "등록 중..." : `${csvRows.length}건 일괄 등록`}
                </button>
              </div>
            </>
          ) : null}
        </>
      )}

      {error ? (
        <p className="text-danger" role="alert">
          {error}
        </p>
      ) : null}

      {result ? (
        <div className="rounded-lg border border-border p-3" role="status">
          <p className="font-medium">
            총 {result.total}건 · 성공 <span className="text-brand">{result.successCount}</span> · 실패{" "}
            <span className={result.failureCount ? "text-danger" : ""}>{result.failureCount}</span>
          </p>
          {result.results.some((r) => !r.success) ? (
            <ul className="mt-2 flex flex-col gap-1 text-xs">
              {result.results
                .filter((r) => !r.success)
                .map((r) => (
                  <li key={r.row} className="text-danger">
                    {r.row}행 {r.orderNumber ?? ""}: {r.message}
                  </li>
                ))}
            </ul>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}
