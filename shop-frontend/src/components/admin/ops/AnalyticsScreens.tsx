"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  adminJson,
  type CategorySales,
  errorMessage,
  type MonthlyProfit,
  num,
  type OperationsDashboard,
  pct,
  type ProductProfit,
  query,
  won,
} from "@/features/admin/ops";
import {
  BarChart,
  buttonClass,
  DonutChart,
  HBarChart,
  inputClass,
  Notice,
  PageHeader,
  primaryButtonClass,
  Section,
  StatCard,
  Table,
  WarningList,
} from "@/components/admin/ops/ui";

const PERIODS: [string, string][] = [
  ["today", "오늘"],
  ["7d", "최근 7일"],
  ["this_month", "이번 달"],
  ["last_month", "지난달"],
  ["custom", "직접 선택"],
];

function changeText(rate: number | null | undefined): string | undefined {
  if (rate === null || rate === undefined) return undefined;
  const n = Number(rate);
  return `전월 동기 대비 ${n >= 0 ? "+" : ""}${(n * 100).toFixed(1)}%`;
}

export function ProductProfitClient() {
  const [period, setPeriod] = useState("this_month");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [applied, setApplied] = useState({ period: "this_month", from: "", to: "" });
  const [data, setData] = useState<ProductProfit | null>(null);
  const [categories, setCategories] = useState<CategorySales[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [sort, setSort] = useState<"netSales" | "grossProfit" | "contributionProfit" | "soldQuantity">("netSales");

  useEffect(() => {
    setError(null);
    const q = query(applied.period === "custom" ? applied : { period: applied.period });
    Promise.all([
      adminJson<ProductProfit>(`/admin/analytics/product-profit${q}`),
      adminJson<CategorySales[]>(`/admin/analytics/category-sales${q}`),
    ])
      .then(([profit, cats]) => {
        setData(profit);
        setCategories(cats);
      })
      .catch((e) => setError(errorMessage(e)));
  }, [applied]);

  const t = data?.totals;
  const rows = [...(data?.products ?? [])].sort((a, b) => Number(b[sort]) - Number(a[sort]));

  return (
    <>
      <PageHeader
        title="상품별 손익"
        description="결제 승인일 기준으로 집계합니다. 순매출 = 총판매 − 할인 − 환불, 매출총이익 = 순매출 − 매출원가, 공헌이익 = 매출총이익 + 배송비 수입 − 결제수수료 − 배송비 − 기타 판매비."
      />
      <form
        className="flex flex-wrap items-center gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          setApplied({ period, from, to });
        }}
      >
        {PERIODS.map(([code, label]) => (
          <button
            key={code}
            type={code === "custom" ? "button" : "submit"}
            className={period === code ? primaryButtonClass : buttonClass}
            onClick={() => setPeriod(code)}
          >
            {label}
          </button>
        ))}
        {period === "custom" ? (
          <>
            <input type="date" className={inputClass} value={from} onChange={(e) => setFrom(e.target.value)} />
            <input type="date" className={inputClass} value={to} onChange={(e) => setTo(e.target.value)} />
            <button type="submit" className={primaryButtonClass}>
              조회
            </button>
          </>
        ) : null}
        {data ? (
          <span className="text-sm text-muted-foreground">
            {data.period.from} ~ {data.period.to} · 매출 기준 {data.revenueBasis === "VAT_EXCLUDED" ? "부가세 제외" : "부가세 포함"}
          </span>
        ) : null}
      </form>
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {t ? (
        <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
          <StatCard label="순매출" value={won(t.netSales)} sub={`주문 ${t.orderCount}건 · ${t.soldQuantity}개`} />
          <StatCard label="할인 / 환불" value={`${won(t.discountAmount)} / ${won(t.refundAmount)}`} />
          <StatCard label="매출원가" value={won(t.costOfGoodsSold)} sub={t.estimatedCostQuantity ? `${t.estimatedCostQuantity}개는 현재 원가로 추정` : undefined} />
          <StatCard label="매출총이익" value={won(t.grossProfit)} sub={`이익률 ${pct(t.grossMarginRate)}`} tone={Number(t.grossProfit) < 0 ? "danger" : undefined} />
          <StatCard label="결제수수료 · 배송비" value={won(Number(t.paymentFee) + Number(t.shippingCost))} sub={`배송비 수입 ${won(t.deliveryRevenue)}`} />
          <StatCard label="공헌이익" value={won(t.contributionProfit)} sub={`이익률 ${pct(t.contributionMarginRate)}`} tone={Number(t.contributionProfit) < 0 ? "danger" : "ok"} />
        </div>
      ) : null}
      <WarningList items={data?.notes} tone="info" />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Section title="카테고리별 매출 비중">
          <DonutChart items={categories.map((c) => ({ name: c.categoryName ?? "미분류", value: Number(c.netSales) }))} />
        </Section>
        <Section title="상품별 매출총이익 (상위 10)">
          <HBarChart items={[...(data?.products ?? [])].sort((a, b) => Number(b.grossProfit) - Number(a.grossProfit)).slice(0, 10).map((p) => ({ name: p.productName, value: Number(p.grossProfit) }))} />
        </Section>
      </div>
      <div className="flex items-center gap-2 text-sm">
        정렬
        <select className={inputClass} value={sort} onChange={(e) => setSort(e.target.value as typeof sort)}>
          <option value="netSales">순매출</option>
          <option value="grossProfit">매출총이익</option>
          <option value="contributionProfit">공헌이익</option>
          <option value="soldQuantity">판매수량</option>
        </select>
      </div>
      <Table>
        <thead>
          <tr>
            <th>상품</th>
            <th>카테고리</th>
            <th className="text-right">판매</th>
            <th className="text-right">환불</th>
            <th className="text-right">총판매</th>
            <th className="text-right">할인</th>
            <th className="text-right">환불액</th>
            <th className="text-right">순매출</th>
            <th className="text-right">매출원가</th>
            <th className="text-right">매출총이익</th>
            <th className="text-right">이익률</th>
            <th className="text-right">수수료</th>
            <th className="text-right">배송비</th>
            <th className="text-right">공헌이익</th>
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={14} className="py-6 text-center text-muted-foreground">
                기간 내 판매가 없습니다.
              </td>
            </tr>
          ) : null}
          {rows.map((p) => (
            <tr key={p.productId}>
              <td>
                <Link href={`/admin/products/${p.productId}/edit`} className="underline">
                  {p.productName}
                </Link>
                {p.estimatedCostQuantity ? <span className="ml-1 text-xs text-amber-700">원가 추정 {p.estimatedCostQuantity}개</span> : null}
              </td>
              <td>{p.categoryName ?? "-"}</td>
              <td className="text-right tabular-nums">{p.soldQuantity}</td>
              <td className="text-right tabular-nums">{p.refundedQuantity}</td>
              <td className="text-right tabular-nums">{won(p.grossSales)}</td>
              <td className="text-right tabular-nums">{won(p.discountAmount)}</td>
              <td className="text-right tabular-nums">{won(p.refundAmount)}</td>
              <td className="text-right tabular-nums">{won(p.netSales)}</td>
              <td className="text-right tabular-nums">{won(p.costOfGoodsSold)}</td>
              <td className={`text-right tabular-nums ${Number(p.grossProfit) < 0 ? "text-danger" : ""}`}>{won(p.grossProfit)}</td>
              <td className="text-right tabular-nums">{pct(p.grossMarginRate)}</td>
              <td className="text-right tabular-nums">{won(p.paymentFee)}</td>
              <td className="text-right tabular-nums">{won(p.shippingCost)}</td>
              <td className={`text-right tabular-nums ${Number(p.contributionProfit) < 0 ? "text-danger" : ""}`}>{won(p.contributionProfit)}</td>
            </tr>
          ))}
        </tbody>
      </Table>
    </>
  );
}

export function MonthlyProfitClient() {
  const [year, setYear] = useState(new Date().getFullYear());
  const [data, setData] = useState<{ year: number; revenueBasis: string; months: MonthlyProfit[]; notes: string[] } | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    adminJson<typeof data>(`/admin/analytics/monthly-profit?year=${year}`)
      .then(setData)
      .catch((e) => setError(errorMessage(e)));
  }, [year]);

  const months = data?.months ?? [];
  return (
    <>
      <PageHeader
        title="월별 손익"
        description="매입액은 매입장부(매입일) 기준, 매출·원가는 결제 승인일 기준입니다. 매입액과 매출원가는 시점이 달라 같지 않을 수 있습니다."
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
      <Section title="월별 매입 · 순매출 · 매출총이익">
        <BarChart
          labels={months.map((m) => m.month.slice(5) + "월")}
          series={[
            { name: "매입액", values: months.map((m) => Number(m.purchaseAmount)) },
            { name: "순매출", values: months.map((m) => Number(m.netSales)) },
          ]}
          lines={[
            { name: "매출총이익", values: months.map((m) => Number(m.grossProfit)), color: "#2e7d32" },
            { name: "공헌이익", values: months.map((m) => Number(m.contributionProfit)), color: "#5b7a8c" },
          ]}
        />
      </Section>
      <WarningList items={data?.notes} tone="info" />
      <Table>
        <thead>
          <tr>
            <th>월</th>
            <th className="text-right">매입액</th>
            <th className="text-right">지급액</th>
            <th className="text-right">순매출</th>
            <th className="text-right">매출원가</th>
            <th className="text-right">매출총이익</th>
            <th className="text-right">이익률</th>
            <th className="text-right">공헌이익</th>
            <th className="text-right">판매수량</th>
          </tr>
        </thead>
        <tbody>
          {months.map((m) => (
            <tr key={m.month}>
              <td>{m.month}</td>
              <td className="text-right tabular-nums">{won(m.purchaseAmount)}</td>
              <td className="text-right tabular-nums">{won(m.paymentAmount)}</td>
              <td className="text-right tabular-nums">{won(m.netSales)}</td>
              <td className="text-right tabular-nums">{won(m.costOfGoodsSold)}</td>
              <td className="text-right tabular-nums">{won(m.grossProfit)}</td>
              <td className="text-right tabular-nums">{pct(m.grossMarginRate)}</td>
              <td className="text-right tabular-nums">{won(m.contributionProfit)}</td>
              <td className="text-right tabular-nums">{num(m.soldQuantity)}</td>
            </tr>
          ))}
        </tbody>
      </Table>
    </>
  );
}

export function OperationsDashboardClient() {
  const [data, setData] = useState<OperationsDashboard | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    adminJson<OperationsDashboard>("/admin/dashboard/operations")
      .then(setData)
      .catch((e) => setError(errorMessage(e)));
  }, []);

  if (error) return <Notice tone="danger">{error}</Notice>;
  if (!data) return <p className="text-sm text-muted-foreground">불러오는 중...</p>;
  const { purchases: p, stock: s, sales, ai } = data;
  return (
    <>
      <PageHeader
        title="통합 대시보드"
        description={`${data.asOf} 기준 · ${data.month}. 숫자는 각 상세 화면(매입장부, 재고 현황, 상품별 손익, AI 사용량)과 같은 계산을 사용합니다.`}
      />
      <Section title="매입">
        <div className="grid gap-3 sm:grid-cols-4">
          <StatCard label="이번 달 매입액" value={won(p.purchaseAmount)} sub={changeText(p.changeRate)} href="/admin/purchases/summary" />
          <StatCard label="이번 달 지급액" value={won(p.paymentAmount)} />
          <StatCard label="이번 달 미지급" value={won(p.unpaidThisMonth)} tone={p.unpaidThisMonth ? "warn" : undefined} />
          <StatCard label="누적 미지급" value={won(p.unpaidTotal)} tone={p.unpaidTotal ? "warn" : undefined} />
        </div>
      </Section>
      <Section title="재고">
        <div className="grid gap-3 sm:grid-cols-4 lg:grid-cols-7">
          <StatCard label="총 재고" value={num(s.totalQuantity)} href="/admin/inventory/status" />
          <StatCard label="재고 금액" value={won(s.stockValue)} />
          <StatCard label="품절" value={s.soldOutCount} tone={s.soldOutCount ? "danger" : "ok"} />
          <StatCard label="재고 부족" value={s.lowStockCount} tone={s.lowStockCount ? "warn" : "ok"} href="/admin/inventory/analysis" />
          <StatCard label="장기 재고" value={s.slowMovingCount} tone={s.slowMovingCount ? "warn" : undefined} href="/admin/inventory/analysis" />
          <StatCard label="원가 미확인" value={s.unknownCostCount} tone={s.unknownCostCount ? "warn" : undefined} />
          <StatCard label="입고 대기" value={s.pendingReceiptCount} href="/admin/inventory/receipts" />
        </div>
      </Section>
      <Section title="판매 (이번 달)">
        <div className="grid gap-3 sm:grid-cols-5">
          <StatCard label="순매출" value={won(sales.netSales)} sub={changeText(sales.netSalesChangeRate)} href="/admin/analytics" />
          <StatCard label="주문 / 판매수량" value={`${sales.orderCount}건 / ${sales.soldQuantity}개`} />
          <StatCard label="매출총이익" value={won(sales.grossProfit)} sub={`이익률 ${pct(sales.grossMarginRate)}`} />
          <StatCard label="공헌이익" value={won(sales.contributionProfit)} tone={Number(sales.contributionProfit) < 0 ? "danger" : undefined} />
          <StatCard label="전월 동기 매출총이익" value={won(sales.previousGrossProfit)} />
        </div>
      </Section>
      <Section title="AI">
        <div className="grid gap-3 sm:grid-cols-4">
          <StatCard
            label="AI 상태"
            value={ai.available ? (ai.mockMode ? "Mock 모드" : "사용 가능") : "사용 불가"}
            tone={ai.available ? (ai.mockMode ? "warn" : "ok") : "danger"}
            href="/admin/ai/operations"
          />
          <StatCard label="이번 달 OpenAI 호출" value={`${ai.callsThisMonth}회`} sub={`실패 ${ai.failuresThisMonth}회`} />
          <StatCard label="토큰 (입력/출력)" value={`${num(ai.inputTokens)} / ${num(ai.outputTokens)}`} />
          <StatCard label="예상 비용" value={ai.estimatedCostUsd === null ? "-" : `$${Number(ai.estimatedCostUsd).toFixed(4)}`} />
        </div>
      </Section>
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Section title="월별 매입 · 매출 추이 (최근 12개월)">
          <BarChart
            labels={data.monthlyTrend.map((m) => m.month.slice(2).replace("-", "."))}
            series={[
              { name: "매입액", values: data.monthlyTrend.map((m) => Number(m.purchaseAmount)) },
              { name: "순매출", values: data.monthlyTrend.map((m) => Number(m.netSales)) },
            ]}
          />
        </Section>
        <Section title="월별 매출총이익 · 공헌이익">
          <BarChart
            labels={data.monthlyTrend.map((m) => m.month.slice(2).replace("-", "."))}
            series={[{ name: "매출총이익", values: data.monthlyTrend.map((m) => Number(m.grossProfit)), color: "#9cb08a" }]}
            lines={[{ name: "공헌이익", values: data.monthlyTrend.map((m) => Number(m.contributionProfit)), color: "#5b7a8c" }]}
          />
        </Section>
        <Section title="카테고리별 매출 비중 (이번 달)">
          <DonutChart items={data.categoryShare.map((c) => ({ name: c.categoryName ?? "미분류", value: Number(c.netSales) }))} />
        </Section>
        <Section title="상품 매출 TOP 10 (이번 달)">
          <HBarChart items={data.productRanking.map((r) => ({ name: r.productName, value: Number(r.netSales) }))} />
        </Section>
        <Section title="거래처별 매입액 TOP 10 (올해)">
          <HBarChart items={data.supplierPurchases.map((r) => ({ name: r.name, value: Number(r.value) }))} />
        </Section>
        <Section title="재고 부족 TOP 10 (추천 수량)">
          <HBarChart
            format={(n) => `${n}개`}
            items={data.lowStockItems.map((r) => ({ name: `${r.productName} ${r.optionName}`, value: r.suggestedQuantity }))}
            emptyText="부족한 재고가 없습니다."
          />
        </Section>
      </div>
      <WarningList items={data.notes} tone="info" />
    </>
  );
}
