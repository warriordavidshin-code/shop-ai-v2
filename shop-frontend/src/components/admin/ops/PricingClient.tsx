"use client";

import { useEffect, useState } from "react";
import {
  adminJson,
  COST_SOURCE_LABELS,
  errorMessage,
  pct,
  postJson,
  type PriceRecommendation,
  type PricingPolicy,
  recommendPrice,
  won,
} from "@/features/admin/ops";
import { Badge, Field, inputClass, Notice, PageHeader, primaryButtonClass, Section, Table, WarningList } from "@/components/admin/ops/ui";

const RATE_KEYS = ["competitiveMarginRate", "standardMarginRate", "premiumMarginRate", "minimumMarginRate", "vatRate", "paymentFeeRate", "otherSellingCostRate"] as const;
type RateKey = (typeof RATE_KEYS)[number];

const INT_KEYS = ["shippingCostPerOrder", "lowStockCoverDays", "safetyStockDays", "defaultLeadDays", "salesWindowDays", "slowMovingDays", "slowMovingMaxSales"] as const;
type IntKey = (typeof INT_KEYS)[number];

const LABELS: Record<RateKey | IntKey, string> = {
  competitiveMarginRate: "경쟁가 목표 마진율 (내부 목표)",
  standardMarginRate: "표준 목표 마진율",
  premiumMarginRate: "프리미엄 목표 마진율",
  minimumMarginRate: "최소 마진율 (이하 경고)",
  vatRate: "부가세율",
  paymentFeeRate: "결제수수료율",
  otherSellingCostRate: "기타 판매비율",
  shippingCostPerOrder: "주문당 배송비 (원)",
  lowStockCoverDays: "재고 부족 판단 소진일수",
  safetyStockDays: "안전재고 일수",
  defaultLeadDays: "기본 리드타임 (일)",
  salesWindowDays: "판매 속도 집계 기간 (일)",
  slowMovingDays: "장기재고 판단 기간 (일)",
  slowMovingMaxSales: "장기재고 판단 최대 판매량",
};

type FormState = Record<RateKey | IntKey, string> & {
  roundingMode: string;
  marginBasis: string;
  taxType: string;
  costingMethod: string;
};

function toForm(p: PricingPolicy): FormState {
  const form = {
    roundingMode: p.roundingMode,
    marginBasis: p.marginBasis,
    taxType: p.taxType,
    costingMethod: p.costingMethod,
  } as FormState;
  RATE_KEYS.forEach((k) => {
    form[k] = String(Math.round(Number(p[k]) * 10000) / 100);
  });
  INT_KEYS.forEach((k) => {
    form[k] = String(p[k]);
  });
  return form;
}

export function PricingClient() {
  const [policy, setPolicy] = useState<PricingPolicy | null>(null);
  const [form, setForm] = useState<FormState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [cost, setCost] = useState("18000");
  const [current, setCurrent] = useState("");
  const [rec, setRec] = useState<PriceRecommendation | null>(null);

  useEffect(() => {
    adminJson<PricingPolicy>("/admin/pricing/policies")
      .then((p) => {
        setPolicy(p);
        setForm(toForm(p));
      })
      .catch((e) => setError(errorMessage(e)));
  }, []);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!form || !policy) return;
    setError(null);
    setSaved(false);
    const body: Record<string, unknown> = {
      roundingMode: form.roundingMode,
      marginBasis: form.marginBasis,
      taxType: form.taxType,
      costingMethod: form.costingMethod,
      version: policy.version,
    };
    RATE_KEYS.forEach((k) => {
      body[k] = Number(form[k]) / 100;
    });
    INT_KEYS.forEach((k) => {
      body[k] = Number(form[k]);
    });
    try {
      const next = await postJson<PricingPolicy>("/admin/pricing/policies", body, "PUT");
      setPolicy(next);
      setForm(toForm(next));
      setSaved(true);
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function simulate() {
    setError(null);
    try {
      setRec(await recommendPrice({ unitCost: Number(cost) || null, currentSalePrice: current ? Number(current) : null }));
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <>
      <PageHeader
        title="가격 정책"
        description="판매가 추천은 이 정책으로 산술 계산합니다. AI는 계산된 가격을 바꾸지 않고 설명만 덧붙입니다. 마진율 = (판매가 − 원가) ÷ 판매가."
      />
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {saved ? <Notice tone="ok">저장했습니다. 변경 이력은 감사 로그에 남습니다.</Notice> : null}
      {form ? (
        <form onSubmit={save} className="flex flex-col gap-4">
          <Section title="마진 · 가격 끝자리">
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {(["competitiveMarginRate", "standardMarginRate", "premiumMarginRate", "minimumMarginRate"] as const).map((k) => (
                <Field key={k} label={`${LABELS[k]} (%)`}>
                  <input className={inputClass} inputMode="decimal" value={form[k]} onChange={(e) => setForm({ ...form, [k]: e.target.value })} />
                </Field>
              ))}
              <Field label="가격 끝자리 처리" hint="900원+9,000원 올림: 37,200→37,900 · 42,100→42,900 · 48,300→49,000">
                <select className={inputClass} value={form.roundingMode} onChange={(e) => setForm({ ...form, roundingMode: e.target.value })}>
                  <option value="END_900_ROUND_9000">900원 끝자리, x8,900원은 x9,000원</option>
                  <option value="END_900">900원 끝자리</option>
                  <option value="UP_1000">1,000원 단위 올림</option>
                  <option value="UP_100">100원 단위 올림</option>
                  <option value="NONE">처리 안 함</option>
                </select>
              </Field>
              <Field label="마진 계산 기준">
                <select className={inputClass} value={form.marginBasis} onChange={(e) => setForm({ ...form, marginBasis: e.target.value })}>
                  <option value="VAT_EXCLUDED">부가세 제외 매출 기준</option>
                  <option value="VAT_INCLUDED">부가세 포함 판매가 기준</option>
                </select>
              </Field>
              <Field label="과세 유형">
                <select className={inputClass} value={form.taxType} onChange={(e) => setForm({ ...form, taxType: e.target.value })}>
                  <option value="GENERAL">일반과세</option>
                  <option value="SIMPLIFIED">간이과세</option>
                  <option value="EXEMPT">면세</option>
                </select>
              </Field>
              <Field label={`${LABELS.vatRate} (%)`}>
                <input className={inputClass} inputMode="decimal" value={form.vatRate} onChange={(e) => setForm({ ...form, vatRate: e.target.value })} />
              </Field>
            </div>
          </Section>
          <Section title="원가 · 판매비용">
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Field label="원가 계산 방식">
                <select className={inputClass} value={form.costingMethod} onChange={(e) => setForm({ ...form, costingMethod: e.target.value })}>
                  <option value="WEIGHTED_AVERAGE">이동평균(가중평균)</option>
                  <option value="RECENT">최근 매입원가</option>
                  <option value="STANDARD">표준원가 (상품 매입가)</option>
                </select>
              </Field>
              <Field label={`${LABELS.paymentFeeRate} (%)`}>
                <input className={inputClass} inputMode="decimal" value={form.paymentFeeRate} onChange={(e) => setForm({ ...form, paymentFeeRate: e.target.value })} />
              </Field>
              <Field label={`${LABELS.otherSellingCostRate} (%)`}>
                <input className={inputClass} inputMode="decimal" value={form.otherSellingCostRate} onChange={(e) => setForm({ ...form, otherSellingCostRate: e.target.value })} />
              </Field>
              <Field label={LABELS.shippingCostPerOrder}>
                <input className={inputClass} inputMode="numeric" value={form.shippingCostPerOrder} onChange={(e) => setForm({ ...form, shippingCostPerOrder: e.target.value })} />
              </Field>
            </div>
            <p className="text-xs text-muted-foreground">판매 배송비·결제수수료는 재고 원가에 넣지 않고 공헌이익 계산에서만 차감합니다.</p>
          </Section>
          <Section title="재고 분석 기준">
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {(["lowStockCoverDays", "safetyStockDays", "defaultLeadDays", "salesWindowDays", "slowMovingDays", "slowMovingMaxSales"] as const).map((k) => (
                <Field key={k} label={LABELS[k]}>
                  <input className={inputClass} inputMode="numeric" value={form[k]} onChange={(e) => setForm({ ...form, [k]: e.target.value })} />
                </Field>
              ))}
            </div>
          </Section>
          <div>
            <button type="submit" className={primaryButtonClass}>
              정책 저장
            </button>
          </div>
        </form>
      ) : null}

      <Section title="판매가 계산기">
        <div className="flex flex-wrap items-end gap-2">
          <Field label="단위 원가 (원)">
            <input className={inputClass} inputMode="numeric" value={cost} onChange={(e) => setCost(e.target.value.replace(/\D/g, ""))} />
          </Field>
          <Field label="현재 판매가 (선택)">
            <input className={inputClass} inputMode="numeric" value={current} onChange={(e) => setCurrent(e.target.value.replace(/\D/g, ""))} />
          </Field>
          <button type="button" className={primaryButtonClass} onClick={() => void simulate()}>
            계산
          </button>
        </div>
        {rec ? <PriceRecommendationView rec={rec} /> : null}
      </Section>
    </>
  );
}

export function PriceRecommendationView({ rec, onPick }: { rec: PriceRecommendation; onPick?: (price: number) => void }) {
  return (
    <div className="flex flex-col gap-2">
      <p className="text-xs text-muted-foreground">
        원가 {won(rec.unitCost)} ({rec.costSource ? COST_SOURCE_LABELS[rec.costSource] ?? rec.costSource : "-"}) · 기준 {rec.marginBasis === "VAT_EXCLUDED" ? "부가세 제외" : "부가세 포함"} · 최소 마진{" "}
        {pct(rec.minimumMarginRate, 0)}
      </p>
      <Table>
        <thead>
          <tr>
            <th>구분</th>
            <th className="text-right">목표 마진</th>
            <th className="text-right">계산가</th>
            <th className="text-right">추천 판매가</th>
            <th className="text-right">실제 마진</th>
            <th className="text-right">매출총이익</th>
            <th>비고</th>
            {onPick ? <th /> : null}
          </tr>
        </thead>
        <tbody>
          {rec.options.map((o) => (
            <tr key={o.tier}>
              <td>
                {o.label}
                {o.tier === "COMPETITIVE" ? <span className="ml-1"><Badge>내부 목표</Badge></span> : null}
              </td>
              <td className="text-right tabular-nums">{pct(o.targetMarginRate, 0)}</td>
              <td className="text-right tabular-nums">{won(o.rawPrice)}</td>
              <td className="text-right font-semibold tabular-nums">{won(o.roundedPrice)}</td>
              <td className={`text-right tabular-nums ${o.belowMinimum ? "text-danger" : ""}`}>{pct(o.marginRate)}</td>
              <td className="text-right tabular-nums">{won(o.grossProfit)}</td>
              <td className="text-xs">{o.belowMinimum ? "최소 마진 미달 " : ""}{o.note ?? ""}</td>
              {onPick ? (
                <td>
                  <button type="button" className="text-xs underline" onClick={() => onPick(o.roundedPrice)}>
                    이 가격 사용
                  </button>
                </td>
              ) : null}
            </tr>
          ))}
        </tbody>
      </Table>
      {rec.current ? (
        <p className={`text-sm ${rec.current.belowMinimum ? "text-danger" : ""}`}>
          현재 판매가 {won(rec.current.salePrice)} → 마진 {pct(rec.current.marginRate)}, 매출총이익 {won(rec.current.grossProfit)}
          {rec.current.belowMinimum ? " (최소 마진 미달)" : ""}
        </p>
      ) : null}
      <WarningList items={rec.warnings} />
      {rec.explanation ? <p className="whitespace-pre-line text-xs text-muted-foreground">{rec.explanation}</p> : null}
    </div>
  );
}
