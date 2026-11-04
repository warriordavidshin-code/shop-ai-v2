"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  adminJson,
  dateTime,
  errorMessage,
  isPaidApprovalRequired,
  num,
  pct,
  postJson,
  query,
  won,
} from "@/features/admin/ops";
import {
  BREAKDOWN_LABELS,
  type DecisionView,
  FEATURE_SOURCE_LABELS,
  type FeatureManualRequest,
  type FeatureView,
  GROUP_LABELS,
  PLACEMENT_LABELS,
  POPULAR_TYPE_LABELS,
  PURCHASE_DECISION_LABELS,
  type PurchaseReport,
  type PurchaseRow,
  type RecoAnalytics,
  type RecoPreview,
  type RecoRule,
  type RecoRuleRequest,
  type RecoSettings,
  RULE_TYPE_LABELS,
  SLOW_CLASS_LABELS,
  type SlowReport,
  type SlowRow,
  TREND_LABELS,
  WEIGHT_FIELDS,
} from "@/features/admin/reco";
import {
  Badge,
  BarChart,
  buttonClass,
  Field,
  inputClass,
  Notice,
  PageHeader,
  primaryButtonClass,
  Section,
  StatCard,
  Table,
  WarningList,
} from "@/components/admin/ops/ui";

const NO_AI_NOTE = "추천 계산은 Java/SQL 규칙으로만 이루어지며 AI 비용이 발생하지 않습니다.";

function useFlash() {
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);
  const view = (
    <>
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {ok ? <Notice tone="ok">{ok}</Notice> : null}
    </>
  );
  return {
    view,
    fail: (e: unknown) => {
      setOk(null);
      setError(errorMessage(e));
    },
    done: (message: string) => {
      setError(null);
      setOk(message);
    },
    clear: () => {
      setError(null);
      setOk(null);
    },
  };
}

// ---------------------------------------------------------------- 1. 설정·가중치

export function RecoSettingsClient() {
  const flash = useFlash();
  const [form, setForm] = useState<RecoSettings | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    adminJson<RecoSettings>("/admin/recommendations/settings").then(setForm).catch(flash.fail);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function save() {
    if (!form) return;
    setBusy(true);
    try {
      setForm(await postJson<RecoSettings>("/admin/recommendations/settings", form, "PUT"));
      flash.done("저장했습니다. 추천 캐시를 비웠으며 다음 요청부터 적용됩니다.");
    } catch (e) {
      flash.fail(e);
    } finally {
      setBusy(false);
    }
  }

  const sum = form ? WEIGHT_FIELDS.reduce((a, f) => a + Number(form[f.key] ?? 0), 0) : 0;
  const setNum = (key: keyof RecoSettings, value: string) => form && setForm({ ...form, [key]: value === "" ? 0 : Number(value) });
  const toggles: { key: keyof RecoSettings; label: string }[] = [
    { key: "enabled", label: "추천 기능 전체" },
    { key: "homeEnabled", label: "메인 화면" },
    { key: "detailEnabled", label: "상품 상세" },
    { key: "cartEnabled", label: "장바구니" },
    { key: "mypageEnabled", label: "마이페이지" },
  ];

  return (
    <>
      <PageHeader title="추천 설정 · 가중치" description={`개인화 추천 점수의 가중치와 노출 위치를 설정합니다. ${NO_AI_NOTE}`} />
      {flash.view}
      {form ? (
        <>
          <Section title="노출 위치">
            <div className="flex flex-wrap gap-4">
              {toggles.map((t) => (
                <label key={t.key} className="inline-flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={Boolean(form[t.key])} onChange={(e) => setForm({ ...form, [t.key]: e.target.checked })} />
                  {t.label}
                </label>
              ))}
            </div>
          </Section>
          <Section title="개인화 점수 가중치">
            <p className="text-xs text-muted-foreground">
              점수 = Σ(항목 점수 × 가중치) ÷ 가중치 합계. 기본값: 카테고리 .25 · 색상 .15 · 가격 .15 · 구매유사 .20 · 인기 .15 · 신상 .10
            </p>
            <div className="grid gap-3 sm:grid-cols-3">
              {WEIGHT_FIELDS.map((f) => (
                <Field key={f.key} label={`${f.label} (${f.formula})`} hint={sum > 0 ? `비중 ${pct(Number(form[f.key]) / sum)}` : undefined}>
                  <input className={inputClass} type="number" step="0.01" min={0} max={9.999} value={String(form[f.key])} onChange={(e) => setNum(f.key, e.target.value)} />
                </Field>
              ))}
            </div>
            <p className={`text-sm ${sum > 0 ? "text-muted-foreground" : "text-danger"}`}>가중치 합계 {sum.toFixed(3)}</p>
          </Section>
          <Section title="운영 값">
            <div className="grid gap-3 sm:grid-cols-3">
              <Field label="노출 개수 (1~30)">
                <input className={inputClass} type="number" value={form.displayCount} onChange={(e) => setNum("displayCount", e.target.value)} />
              </Field>
              <Field label="추천 캐시 (분)">
                <input className={inputClass} type="number" value={form.cacheMinutes} onChange={(e) => setNum("cacheMinutes", e.target.value)} />
              </Field>
              <Field label="최소 재고 (이하 제외)">
                <input className={inputClass} type="number" value={form.minStock} onChange={(e) => setNum("minStock", e.target.value)} />
              </Field>
              <Field label="구매 기여 인정 기간 (일)">
                <input className={inputClass} type="number" value={form.attributionDays} onChange={(e) => setNum("attributionDays", e.target.value)} />
              </Field>
              <Field label="기본 인기 집계 기간 (일)">
                <input className={inputClass} type="number" value={form.popularDays} onChange={(e) => setNum("popularDays", e.target.value)} />
              </Field>
              <Field label="재고 소진 최대 할인율 (%)" hint="최소 마진율보다 낮은 가격은 제안하지 않습니다.">
                <input
                  className={inputClass}
                  type="number"
                  step="1"
                  value={Math.round(Number(form.maxDiscountRate) * 100)}
                  onChange={(e) => setForm({ ...form, maxDiscountRate: e.target.value === "" ? 0 : Number(e.target.value) / 100 })}
                />
              </Field>
            </div>
          </Section>
          <div className="flex items-center gap-3">
            <button type="button" className={primaryButtonClass} disabled={busy} onClick={() => void save()}>
              저장
            </button>
            <span className="text-xs text-muted-foreground">마지막 수정 {dateTime(form.updatedAt)}</span>
          </div>
        </>
      ) : null}
    </>
  );
}

// ---------------------------------------------------------------- 2. 규칙

const EMPTY_RULE: RecoRuleRequest = { ruleType: "COORDINATION", sourceValue: "", targetValue: "", productId: null, weight: 1, memo: "", active: true };

export function RecoRulesClient() {
  const flash = useFlash();
  const [rules, setRules] = useState<RecoRule[]>([]);
  const [type, setType] = useState("");
  const [form, setForm] = useState<RecoRuleRequest>(EMPTY_RULE);
  const [editing, setEditing] = useState<number | null>(null);

  const load = useCallback(() => {
    adminJson<RecoRule[]>("/admin/recommendations/rules").then(setRules).catch(flash.fail);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => load(), [load]);

  async function submit() {
    try {
      const body = { ...form, productId: form.productId || null };
      if (editing) await postJson(`/admin/recommendations/rules/${editing}`, body, "PUT");
      else await postJson("/admin/recommendations/rules", body);
      flash.done(editing ? "규칙을 수정했습니다." : "규칙을 추가했습니다.");
      setForm(EMPTY_RULE);
      setEditing(null);
      load();
    } catch (e) {
      flash.fail(e);
    }
  }

  async function deactivate(id: number) {
    if (!window.confirm("이 규칙을 비활성화할까요? (이력 보존을 위해 삭제하지 않습니다)")) return;
    try {
      await adminJson(`/admin/recommendations/rules/${id}`, { method: "DELETE" });
      flash.done("규칙을 비활성화했습니다.");
      load();
    } catch (e) {
      flash.fail(e);
    }
  }

  const shown = rules.filter((r) => !type || r.ruleType === type);

  return (
    <>
      <PageHeader
        title="추천 규칙 관리"
        description="코디 규칙(기준 품목 → 추천 품목), 추천 제외 상품, 카테고리 가중치를 관리합니다. 품목은 카테고리명·상품 유형(니트, 슬랙스 등)·대분류(TOP/BOTTOM/DRESS/OUTER)로 입력합니다."
      />
      {flash.view}
      <Section title={editing ? `규칙 #${editing} 수정` : "규칙 추가"}>
        <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
          <Field label="종류">
            <select className={inputClass} value={form.ruleType} onChange={(e) => setForm({ ...form, ruleType: e.target.value })}>
              {Object.entries(RULE_TYPE_LABELS).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </Field>
          {form.ruleType === "EXCLUDE_PRODUCT" ? (
            <Field label="상품 ID">
              <input className={inputClass} inputMode="numeric" value={form.productId ?? ""} onChange={(e) => setForm({ ...form, productId: e.target.value ? Number(e.target.value) : null })} />
            </Field>
          ) : (
            <>
              <Field label={form.ruleType === "COORDINATION" ? "기준 품목" : "대상 카테고리·품목"}>
                <input className={inputClass} value={form.sourceValue ?? ""} onChange={(e) => setForm({ ...form, sourceValue: e.target.value })} placeholder="예: 니트" />
              </Field>
              {form.ruleType === "COORDINATION" ? (
                <Field label="추천 품목">
                  <input className={inputClass} value={form.targetValue ?? ""} onChange={(e) => setForm({ ...form, targetValue: e.target.value })} placeholder="예: 슬랙스" />
                </Field>
              ) : null}
            </>
          )}
          <Field label="가중치 (0~5)">
            <input className={inputClass} type="number" step="0.1" value={form.weight ?? ""} onChange={(e) => setForm({ ...form, weight: e.target.value === "" ? null : Number(e.target.value) })} />
          </Field>
          <Field label="메모">
            <input className={inputClass} value={form.memo ?? ""} onChange={(e) => setForm({ ...form, memo: e.target.value })} />
          </Field>
          <div className="flex items-end gap-2">
            <button type="button" className={primaryButtonClass} onClick={() => void submit()}>
              {editing ? "수정" : "추가"}
            </button>
            {editing ? (
              <button type="button" className={buttonClass} onClick={() => { setEditing(null); setForm(EMPTY_RULE); }}>
                취소
              </button>
            ) : null}
          </div>
        </div>
      </Section>
      <Section
        title={`규칙 목록 (${shown.length})`}
        actions={
          <select className={inputClass} value={type} onChange={(e) => setType(e.target.value)}>
            <option value="">전체</option>
            {Object.entries(RULE_TYPE_LABELS).map(([k, v]) => (
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
              <th>ID</th>
              <th>종류</th>
              <th>기준</th>
              <th>추천 / 상품</th>
              <th className="text-right">가중치</th>
              <th>메모</th>
              <th>상태</th>
              <th>수정일</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {shown.map((r) => (
              <tr key={r.id} className={r.active ? "" : "opacity-50"}>
                <td className="tabular-nums">{r.id}</td>
                <td>{RULE_TYPE_LABELS[r.ruleType] ?? r.ruleType}</td>
                <td>{r.sourceValue ?? "-"}</td>
                <td>
                  {r.ruleType === "EXCLUDE_PRODUCT" && r.productId ? (
                    <Link href={`/admin/products/${r.productId}/edit`} className="underline">
                      상품 #{r.productId}
                    </Link>
                  ) : (
                    r.targetValue ?? "-"
                  )}
                </td>
                <td className="text-right tabular-nums">{Number(r.weight).toFixed(2)}</td>
                <td className="text-xs">{r.memo ?? ""}</td>
                <td>{r.active ? <Badge tone="ok">사용</Badge> : <Badge>비활성</Badge>}</td>
                <td className="whitespace-nowrap">{dateTime(r.updatedAt)}</td>
                <td className="whitespace-nowrap">
                  <button
                    type="button"
                    className="mr-2 text-xs underline"
                    onClick={() => {
                      setEditing(r.id);
                      setForm({ ruleType: r.ruleType, sourceValue: r.sourceValue, targetValue: r.targetValue, productId: r.productId, weight: Number(r.weight), memo: r.memo, active: true });
                    }}
                  >
                    {r.active ? "수정" : "다시 사용"}
                  </button>
                  {r.active ? (
                    <button type="button" className="text-xs text-danger underline" onClick={() => void deactivate(r.id)}>
                      비활성화
                    </button>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      </Section>
    </>
  );
}

// ---------------------------------------------------------------- 3. 미리보기

function PreviewTable({ preview }: { preview: RecoPreview }) {
  return (
    <Section title={`결과 — ${preview.algorithm}`}>
      <p className="text-xs text-muted-foreground">{preview.basis}</p>
      <Table>
        <thead>
          <tr>
            <th className="text-right">순위</th>
            <th>상품</th>
            <th>대분류</th>
            <th className="text-right">가격</th>
            <th className="text-right">재고</th>
            <th className="text-right">점수</th>
            <th>점수 근거</th>
            <th>추천 사유</th>
          </tr>
        </thead>
        <tbody>
          {preview.items.length === 0 ? (
            <tr>
              <td colSpan={8} className="py-6 text-center text-muted-foreground">
                추천할 상품이 없습니다. (품절·판매중지·삭제 상품과 제외 상품은 표시되지 않습니다)
              </td>
            </tr>
          ) : null}
          {preview.items.map((it) => (
            <tr key={it.productId}>
              <td className="text-right tabular-nums">{it.rank}</td>
              <td>
                <Link href={`/admin/products/${it.productId}/edit`} className="underline">
                  {it.productName}
                </Link>
              </td>
              <td>{it.group ? GROUP_LABELS[it.group] ?? it.group : "-"}</td>
              <td className="text-right tabular-nums">{won(it.price)}</td>
              <td className="text-right tabular-nums">{num(it.stock)}</td>
              <td className="text-right font-semibold tabular-nums">{Number(it.score).toFixed(3)}</td>
              <td className="text-xs">
                {Object.entries(it.breakdown ?? {}).map(([k, v]) => (
                  <span key={k} className="mr-2 inline-block">
                    {BREAKDOWN_LABELS[k] ?? k} {Number(v).toFixed(2)}
                  </span>
                ))}
              </td>
              <td className="text-xs">{it.reason ?? ""}</td>
            </tr>
          ))}
        </tbody>
      </Table>
    </Section>
  );
}

export function RecoPreviewClient() {
  const flash = useFlash();
  const [mode, setMode] = useState<"personal" | "similar" | "coordination">("personal");
  const [id, setId] = useState("");
  const [preview, setPreview] = useState<RecoPreview | null>(null);

  async function run() {
    if (!/^\d+$/.test(id.trim())) {
      flash.fail(new Error(mode === "personal" ? "회원 ID를 입력해 주세요." : "상품 ID를 입력해 주세요."));
      return;
    }
    flash.clear();
    try {
      const path = mode === "personal" ? `/admin/recommendations/preview/personal?memberId=${id.trim()}` : `/admin/recommendations/preview/${mode}/${id.trim()}`;
      setPreview(await adminJson<RecoPreview>(path));
    } catch (e) {
      setPreview(null);
      flash.fail(e);
    }
  }

  return (
    <>
      <PageHeader title="추천 미리보기" description="특정 회원·상품 기준으로 고객에게 보일 추천 결과와 점수 근거를 확인합니다. 미리보기는 추적 이벤트를 남기지 않습니다." />
      {flash.view}
      <Section title="조건">
        <div className="flex flex-wrap items-end gap-2">
          <Field label="유형">
            <select className={inputClass} value={mode} onChange={(e) => { setMode(e.target.value as typeof mode); setPreview(null); }}>
              <option value="personal">회원 맞춤 추천</option>
              <option value="similar">비슷한 상품</option>
              <option value="coordination">코디 추천</option>
            </select>
          </Field>
          <Field label={mode === "personal" ? "회원 ID" : "상품 ID"}>
            <input className={inputClass} inputMode="numeric" value={id} onChange={(e) => setId(e.target.value)} onKeyDown={(e) => e.key === "Enter" && void run()} />
          </Field>
          <button type="button" className={primaryButtonClass} onClick={() => void run()}>
            미리보기
          </button>
        </div>
      </Section>
      {preview ? <PreviewTable preview={preview} /> : null}
    </>
  );
}

// ---------------------------------------------------------------- 4. 인기 상품

export function RecoPopularClient() {
  const flash = useFlash();
  const [type, setType] = useState("TOP10");
  const [period, setPeriod] = useState(7);
  const [preview, setPreview] = useState<RecoPreview | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    adminJson<RecoPreview>(`/admin/recommendations/popular${query({ type, period, limit: 20 })}`).then(setPreview).catch(flash.fail);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type, period]);

  useEffect(() => load(), [load]);

  async function refresh() {
    setBusy(true);
    try {
      await postJson("/admin/recommendations/popular/refresh", {});
      flash.done("인기 순위를 다시 집계했습니다.");
      load();
    } catch (e) {
      flash.fail(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader
        title="인기 상품"
        description="조회·장바구니·구매를 가중 합산한 7일/30일 인기 순위입니다. 봇과 같은 사용자의 반복 조회는 제외되며, 정기 스케줄로 집계·캐시됩니다."
        actions={
          <button type="button" className={buttonClass} disabled={busy} onClick={() => void refresh()}>
            {busy ? "집계 중..." : "지금 다시 집계"}
          </button>
        }
      />
      {flash.view}
      <div className="flex flex-wrap gap-2">
        <select className={inputClass} value={type} onChange={(e) => setType(e.target.value)}>
          {Object.entries(POPULAR_TYPE_LABELS).map(([k, v]) => (
            <option key={k} value={k}>
              {v}
            </option>
          ))}
        </select>
        {[7, 30].map((p) => (
          <button key={p} type="button" className={period === p ? primaryButtonClass : buttonClass} onClick={() => setPeriod(p)}>
            최근 {p}일
          </button>
        ))}
      </div>
      {preview ? <PreviewTable preview={preview} /> : null}
    </>
  );
}

// ---------------------------------------------------------------- 5. 매입 추천

function DecisionLog({ type, version }: { type: "PURCHASE" | "SLOW_MOVING"; version: number }) {
  const [rows, setRows] = useState<DecisionView[]>([]);
  useEffect(() => {
    adminJson<DecisionView[]>(`/admin/recommendations/decisions?type=${type}&limit=30`).then(setRows).catch(() => setRows([]));
  }, [type, version]);
  return (
    <Section title="최근 결정 기록">
      <Table>
        <thead>
          <tr>
            <th>일시</th>
            <th>상품</th>
            <th>결정</th>
            <th className="text-right">수량</th>
            <th>내용</th>
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={5} className="py-6 text-center text-muted-foreground">
                기록이 없습니다.
              </td>
            </tr>
          ) : null}
          {rows.map((r) => (
            <tr key={r.id}>
              <td className="whitespace-nowrap">{dateTime(r.decidedAt)}</td>
              <td>
                <Link href={`/admin/products/${r.productId}/edit`} className="underline">
                  #{r.productId}
                </Link>
                {r.skuId ? <span className="text-xs text-muted-foreground"> · SKU {r.skuId}</span> : null}
              </td>
              <td>{r.status === "ACCEPTED" ? <Badge tone="ok">승인</Badge> : <Badge>보류</Badge>}</td>
              <td className="text-right tabular-nums">{r.quantity ?? "-"}</td>
              <td className="max-w-[28rem] text-xs">{r.reason}</td>
            </tr>
          ))}
        </tbody>
      </Table>
    </Section>
  );
}

export function RecoPurchaseClient() {
  const flash = useFlash();
  const [filter, setFilter] = useState("REORDER");
  const [report, setReport] = useState<PurchaseReport | null>(null);
  const [qty, setQty] = useState<Record<number, string>>({});
  const [version, setVersion] = useState(0);

  useEffect(() => {
    adminJson<PurchaseReport>(`/admin/recommendations/purchase?filter=${filter}`).then(setReport).catch(flash.fail);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filter]);

  async function decide(row: PurchaseRow, decision: "ACCEPTED" | "DISMISSED") {
    const quantity = Number(qty[row.skuId] ?? row.suggestedQuantity);
    try {
      await postJson("/admin/recommendations/decisions", {
        type: "PURCHASE",
        productId: row.productId,
        skuId: row.skuId,
        decision,
        quantity: Number.isFinite(quantity) ? quantity : null,
        note: `${row.productName} ${row.optionName} · 제안 ${row.suggestedQuantity}개`,
        metrics: { sales7: row.sales7, sales30: row.sales30, dailySales: row.dailySales, coverDays: row.coverDays, trend: row.trend, formula: row.formula },
      });
      flash.done(decision === "ACCEPTED" ? "승인을 기록했습니다. 실제 발주는 매입 등록에서 직접 진행해 주세요." : "보류를 기록했습니다.");
      setVersion((v) => v + 1);
    } catch (e) {
      flash.fail(e);
    }
  }

  return (
    <>
      <PageHeader
        title="매입 추천"
        description="최근 판매 속도·추세·재고·입고 예정·리드타임으로 옵션(SKU)별 추천 발주 수량을 계산합니다. 자동 발주는 하지 않으며, 승인해도 결정만 기록됩니다."
        actions={
          <Link href="/admin/purchases/new" className={buttonClass}>
            매입 등록
          </Link>
        }
      />
      {flash.view}
      <WarningList items={report?.criteria} tone="info" />
      <div className="flex gap-2">
        <button type="button" className={filter === "REORDER" ? primaryButtonClass : buttonClass} onClick={() => setFilter("REORDER")}>
          발주 검토 대상
        </button>
        <button type="button" className={filter === "ALL" ? primaryButtonClass : buttonClass} onClick={() => setFilter("ALL")}>
          전체 옵션
        </button>
      </div>
      <Table>
        <thead>
          <tr>
            <th className="text-right">우선</th>
            <th>상품 / 옵션</th>
            <th className="text-right">가용 / 입고예정</th>
            <th className="text-right">7일 / 30일 판매</th>
            <th className="text-right">일평균</th>
            <th>추세</th>
            <th className="text-right">소진 예상</th>
            <th className="text-right">리드타임</th>
            <th className="text-right">마진율</th>
            <th>판단</th>
            <th className="text-right">추천 수량</th>
            <th>계산 근거</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {report?.rows.length === 0 ? (
            <tr>
              <td colSpan={13} className="py-6 text-center text-muted-foreground">
                발주가 필요한 옵션이 없습니다.
              </td>
            </tr>
          ) : null}
          {report?.rows.map((r) => (
            <tr key={r.skuId}>
              <td className="text-right tabular-nums">{r.priority}</td>
              <td>
                <Link href={`/admin/products/${r.productId}/edit`} className="underline">
                  {r.productName}
                </Link>{" "}
                <span className="text-xs text-muted-foreground">{r.optionName}</span>
              </td>
              <td className="text-right tabular-nums">
                {num(r.available)} / {num(r.pendingInbound)}
              </td>
              <td className="text-right tabular-nums">
                {num(r.sales7)} / {num(r.sales30)}
              </td>
              <td className="text-right tabular-nums">{Number(r.dailySales).toFixed(2)}</td>
              <td>
                <Badge tone={r.trend === "UP" || r.trend === "NEW_DEMAND" ? "ok" : r.trend === "DOWN" ? "warn" : "muted"}>
                  {TREND_LABELS[r.trend] ?? r.trend}
                  {r.trendRate !== null ? ` ${pct(r.trendRate, 0)}` : ""}
                </Badge>
              </td>
              <td className="text-right tabular-nums">{r.coverDays === null ? "-" : `${Number(r.coverDays).toFixed(0)}일`}</td>
              <td className="text-right tabular-nums">{r.leadDays}일</td>
              <td className="text-right tabular-nums">{pct(r.marginRate)}</td>
              <td>{PURCHASE_DECISION_LABELS[r.decision] ?? r.decision}</td>
              <td className="text-right">
                <input
                  className={`${inputClass} h-8 w-20 text-right`}
                  inputMode="numeric"
                  value={qty[r.skuId] ?? String(r.suggestedQuantity)}
                  onChange={(e) => setQty({ ...qty, [r.skuId]: e.target.value })}
                />
              </td>
              <td className="max-w-[22rem] text-xs">
                <p className="font-mono">{r.formula}</p>
                <ul className="list-disc pl-4 text-muted-foreground">
                  {r.reasons.map((reason, i) => (
                    <li key={i}>{reason}</li>
                  ))}
                </ul>
              </td>
              <td className="whitespace-nowrap">
                <button type="button" className="mr-2 text-xs underline" onClick={() => void decide(r, "ACCEPTED")}>
                  승인
                </button>
                <button type="button" className="text-xs underline" onClick={() => void decide(r, "DISMISSED")}>
                  보류
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </Table>
      <DecisionLog type="PURCHASE" version={version} />
    </>
  );
}

// ---------------------------------------------------------------- 6. 재고 소진

export function RecoSlowMovingClient() {
  const flash = useFlash();
  const [type, setType] = useState("");
  const [report, setReport] = useState<SlowReport | null>(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    adminJson<SlowReport>(`/admin/recommendations/slow-moving${query({ type })}`).then(setReport).catch(flash.fail);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type]);

  async function decide(row: SlowRow, decision: "ACCEPTED" | "DISMISSED") {
    try {
      await postJson("/admin/recommendations/decisions", {
        type: "SLOW_MOVING",
        productId: row.productId,
        skuId: null,
        decision,
        quantity: row.stock,
        note: `${row.productName} · ${row.classes.map((c) => SLOW_CLASS_LABELS[c] ?? c).join(", ")} · 제안가 ${won(row.suggestedPrice)} (하한 ${won(row.floorPrice)})`,
        metrics: { classes: row.classes, strategies: row.strategies, suggestedDiscountRate: row.suggestedDiscountRate, suggestedPrice: row.suggestedPrice, floorPrice: row.floorPrice },
      });
      flash.done(decision === "ACCEPTED" ? "승인을 기록했습니다. 판매가는 자동으로 바뀌지 않으며, 상품 수정에서 직접 변경해 주세요." : "보류를 기록했습니다.");
      setVersion((v) => v + 1);
    } catch (e) {
      flash.fail(e);
    }
  }

  return (
    <>
      <PageHeader
        title="재고 소진 추천"
        description="장기 미판매·과잉 재고·시즌 종료 상품을 분류하고 소진 전략과 할인안을 제안합니다. 제안 가격은 최소 마진율 아래로 내려가지 않으며, 관리자 승인 없이 가격을 바꾸지 않습니다."
      />
      {flash.view}
      {report ? (
        <div className="grid gap-3 sm:grid-cols-3">
          <StatCard label="대상 상품" value={`${num(report.rows.length)}개`} />
          <StatCard label="최소 마진율 (가격 정책)" value={pct(report.minimumMarginRate)} />
          <StatCard label="최대 할인율 (추천 설정)" value={pct(report.maxDiscountRate)} />
        </div>
      ) : null}
      <WarningList items={report?.criteria} tone="info" />
      <div className="flex flex-wrap gap-2">
        <button type="button" className={type === "" ? primaryButtonClass : buttonClass} onClick={() => setType("")}>
          전체
        </button>
        {Object.entries(SLOW_CLASS_LABELS).map(([k, v]) => (
          <button key={k} type="button" className={type === k ? primaryButtonClass : buttonClass} onClick={() => setType(k)}>
            {v}
          </button>
        ))}
      </div>
      <Table>
        <thead>
          <tr>
            <th>상품</th>
            <th>분류</th>
            <th className="text-right">재고</th>
            <th className="text-right">30일 / 90일 판매</th>
            <th className="text-right">마지막 판매</th>
            <th className="text-right">재고 금액</th>
            <th className="text-right">현재가 / 마진</th>
            <th className="text-right">제안 할인 / 제안가</th>
            <th className="text-right">하한가 / 할인 후 마진</th>
            <th>전략 · 근거</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {report?.rows.length === 0 ? (
            <tr>
              <td colSpan={11} className="py-6 text-center text-muted-foreground">
                해당하는 상품이 없습니다.
              </td>
            </tr>
          ) : null}
          {report?.rows.map((r) => (
            <tr key={r.productId}>
              <td>
                <Link href={`/admin/products/${r.productId}/edit`} className="underline">
                  {r.productName}
                </Link>
                <p className="text-xs text-muted-foreground">
                  {r.category ?? ""} {r.season ? `· ${r.season}` : ""}
                </p>
              </td>
              <td className="text-xs">
                {r.classes.map((c) => (
                  <span key={c} className="mb-0.5 mr-1 inline-block">
                    <Badge tone={c === "LONG_UNSOLD" || c === "SEASON_ENDING" ? "warn" : "muted"}>{SLOW_CLASS_LABELS[c] ?? c}</Badge>
                  </span>
                ))}
              </td>
              <td className="text-right tabular-nums">{num(r.stock)}</td>
              <td className="text-right tabular-nums">
                {num(r.sales30)} / {num(r.sales90)}
              </td>
              <td className="text-right tabular-nums">{r.daysSinceLastSale === null ? "판매 없음" : `${r.daysSinceLastSale}일 전`}</td>
              <td className="text-right tabular-nums">{won(r.stockValue)}</td>
              <td className="text-right tabular-nums">
                {won(r.price)}
                <br />
                <span className="text-xs text-muted-foreground">{pct(r.marginRate)}</span>
              </td>
              <td className="text-right tabular-nums">
                {r.suggestedDiscountRate === null ? "-" : pct(r.suggestedDiscountRate, 0)}
                <br />
                <span className="font-semibold">{won(r.suggestedPrice)}</span>
              </td>
              <td className="text-right tabular-nums">
                {won(r.floorPrice)}
                <br />
                <span className="text-xs text-muted-foreground">{pct(r.marginAfterDiscount)}</span>
              </td>
              <td className="max-w-[24rem] text-xs">
                <ul className="list-disc pl-4">
                  {r.strategies.map((s, i) => (
                    <li key={i}>{s}</li>
                  ))}
                </ul>
                <ul className="list-disc pl-4 text-muted-foreground">
                  {r.reasons.map((s, i) => (
                    <li key={i}>{s}</li>
                  ))}
                </ul>
              </td>
              <td className="whitespace-nowrap">
                <button type="button" className="mr-2 text-xs underline" onClick={() => void decide(r, "ACCEPTED")}>
                  승인
                </button>
                <button type="button" className="text-xs underline" onClick={() => void decide(r, "DISMISSED")}>
                  보류
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </Table>
      <DecisionLog type="SLOW_MOVING" version={version} />
    </>
  );
}

// ---------------------------------------------------------------- 7. 성과 분석

export function RecoAnalyticsClient() {
  const flash = useFlash();
  const [days, setDays] = useState(30);
  const [data, setData] = useState<RecoAnalytics | null>(null);

  useEffect(() => {
    adminJson<RecoAnalytics>(`/admin/recommendations/analytics?days=${days}`).then(setData).catch(flash.fail);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [days]);

  return (
    <>
      <PageHeader
        title="추천 성과 분석"
        description="추천 노출·클릭·장바구니·구매 전환과 추천을 통한 매출을 확인합니다. 구매는 결제 전 일정 기간 안에 추천을 클릭한 경우에만 추천 기여로 인정합니다."
        actions={
          <select className={inputClass} value={days} onChange={(e) => setDays(Number(e.target.value))}>
            <option value={7}>최근 7일</option>
            <option value={30}>최근 30일</option>
            <option value={90}>최근 90일</option>
          </select>
        }
      />
      {flash.view}
      {data ? (
        <>
          <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
            <StatCard label="노출" value={num(data.impressions)} />
            <StatCard label="클릭" value={num(data.clicks)} sub={`CTR ${pct(data.impressions ? data.clicks / data.impressions : 0)}`} />
            <StatCard label="장바구니" value={num(data.carts)} />
            <StatCard label="구매" value={num(data.purchases)} sub={`전환율 ${pct(data.clicks ? data.purchases / data.clicks : 0)}`} />
            <StatCard label="추천 매출" value={won(data.recommendationRevenue)} sub={`기여도 ${pct(data.revenueContribution)}`} />
            <StatCard label="일반 매출" value={won(data.normalRevenue)} sub={`전체 ${won(data.totalRevenue)}`} />
          </div>
          <Section title="일별 추이">
            <BarChart
              labels={data.daily.map((d) => d.date.slice(5))}
              series={[
                { name: "노출", values: data.daily.map((d) => d.impressions) },
                { name: "클릭", values: data.daily.map((d) => d.clicks) },
              ]}
              lines={[{ name: "구매", values: data.daily.map((d) => d.purchases) }]}
            />
          </Section>
          <Section title={`위치별 성과 (구매 기여 기간 ${data.attributionDays}일)`}>
            <Table>
              <thead>
                <tr>
                  <th>추천 위치</th>
                  <th>그룹</th>
                  <th className="text-right">노출</th>
                  <th className="text-right">클릭</th>
                  <th className="text-right">CTR</th>
                  <th className="text-right">장바구니</th>
                  <th className="text-right">장바구니율</th>
                  <th className="text-right">구매</th>
                  <th className="text-right">전환율</th>
                  <th className="text-right">매출</th>
                </tr>
              </thead>
              <tbody>
                {data.placements.length === 0 ? (
                  <tr>
                    <td colSpan={10} className="py-6 text-center text-muted-foreground">
                      집계된 추천 이벤트가 없습니다.
                    </td>
                  </tr>
                ) : null}
                {data.placements.map((p) => (
                  <tr key={p.placement + p.variant}>
                    <td>{PLACEMENT_LABELS[p.placement] ?? p.placement}</td>
                    <td>{p.variant}</td>
                    <td className="text-right tabular-nums">{num(p.impressions)}</td>
                    <td className="text-right tabular-nums">{num(p.clicks)}</td>
                    <td className="text-right tabular-nums">{pct(p.ctr)}</td>
                    <td className="text-right tabular-nums">{num(p.carts)}</td>
                    <td className="text-right tabular-nums">{pct(p.cartRate)}</td>
                    <td className="text-right tabular-nums">{num(p.purchases)}</td>
                    <td className="text-right tabular-nums">{pct(p.conversionRate)}</td>
                    <td className="text-right tabular-nums">{won(p.revenue)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </Section>
          <WarningList items={data.notes} tone="info" />
        </>
      ) : null}
    </>
  );
}

// ---------------------------------------------------------------- 8. 상품 특성

const EMPTY_FEATURE: FeatureManualRequest = {
  categoryGroup: null,
  itemType: null,
  colors: null,
  colorTone: null,
  styleTags: null,
  designTags: null,
  season: null,
  material: null,
  keywords: null,
};

export function RecoFeaturesClient() {
  const flash = useFlash();
  const [keyword, setKeyword] = useState("");
  const [rows, setRows] = useState<FeatureView[]>([]);
  const [busy, setBusy] = useState<number | "refresh" | null>(null);
  const [edit, setEdit] = useState<{ productId: number; form: FeatureManualRequest } | null>(null);

  const load = useCallback((kw: string) => {
    adminJson<FeatureView[]>(`/admin/recommendations/features${query({ keyword: kw, limit: 200 })}`).then(setRows).catch(flash.fail);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => load(""), [load]);

  async function refresh() {
    setBusy("refresh");
    try {
      const r = await postJson<{ updated: number }>("/admin/recommendations/features/refresh", {});
      flash.done(`규칙 기반 특성을 ${r.updated}건 갱신했습니다. (AI·수동 입력값은 유지)`);
      load(keyword);
    } catch (e) {
      flash.fail(e);
    } finally {
      setBusy(null);
    }
  }

  async function analyze(productId: number, allowPaid = false) {
    setBusy(productId);
    try {
      await postJson(`/admin/recommendations/features/${productId}/analyze`, { forceRefresh: allowPaid, allowPaid });
      flash.done("AI 이미지 분석 결과로 특성을 저장했습니다.");
      load(keyword);
    } catch (e) {
      if (isPaidApprovalRequired(e)) {
        if (window.confirm(`${errorMessage(e)}\n\n유료 AI(OpenAI)를 1회 호출할까요? 비용이 발생할 수 있습니다.`)) {
          setBusy(null);
          await analyze(productId, true);
          return;
        }
      }
      flash.fail(e);
    } finally {
      setBusy(null);
    }
  }

  async function saveManual() {
    if (!edit) return;
    try {
      await postJson(`/admin/recommendations/features/${edit.productId}`, edit.form, "PUT");
      flash.done("수동 입력값을 저장했습니다.");
      setEdit(null);
      load(keyword);
    } catch (e) {
      flash.fail(e);
    }
  }

  const f = edit?.form;
  const setF = (key: keyof FeatureManualRequest, value: string) => edit && setEdit({ ...edit, form: { ...edit.form, [key]: value || null } });

  return (
    <>
      <PageHeader
        title="상품 특성 관리"
        description="추천에 쓰이는 상품 특성(대분류·품목·색상·스타일·시즌)입니다. 기본은 상품 정보로 자동 생성되며(비용 없음), 필요할 때만 AI 이미지 분석(무료 Gemini 우선)이나 수동 입력으로 보완합니다."
        actions={
          <button type="button" className={buttonClass} disabled={busy === "refresh"} onClick={() => void refresh()}>
            {busy === "refresh" ? "갱신 중..." : "규칙 기반 전체 갱신"}
          </button>
        }
      />
      {flash.view}
      <div className="flex gap-2">
        <input className={`${inputClass} min-w-[16rem]`} placeholder="상품명 검색" value={keyword} onChange={(e) => setKeyword(e.target.value)} onKeyDown={(e) => e.key === "Enter" && load(keyword)} />
        <button type="button" className={buttonClass} onClick={() => load(keyword)}>
          검색
        </button>
      </div>
      {edit && f ? (
        <Section title={`상품 #${edit.productId} 특성 수동 입력`}>
          <div className="grid gap-3 sm:grid-cols-3">
            <Field label="대분류">
              <select className={inputClass} value={f.categoryGroup ?? ""} onChange={(e) => setF("categoryGroup", e.target.value)}>
                <option value="">자동</option>
                {Object.entries(GROUP_LABELS).map(([k, v]) => (
                  <option key={k} value={k}>
                    {v}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="품목 (예: 니트, 슬랙스)">
              <input className={inputClass} value={f.itemType ?? ""} onChange={(e) => setF("itemType", e.target.value)} />
            </Field>
            <Field label="색상 (쉼표 구분)">
              <input className={inputClass} value={f.colors ?? ""} onChange={(e) => setF("colors", e.target.value)} />
            </Field>
            <Field label="색상 톤 (예: 뉴트럴, 웜)">
              <input className={inputClass} value={f.colorTone ?? ""} onChange={(e) => setF("colorTone", e.target.value)} />
            </Field>
            <Field label="스타일 태그 (쉼표 구분)">
              <input className={inputClass} value={f.styleTags ?? ""} onChange={(e) => setF("styleTags", e.target.value)} />
            </Field>
            <Field label="디자인 태그 (쉼표 구분)">
              <input className={inputClass} value={f.designTags ?? ""} onChange={(e) => setF("designTags", e.target.value)} />
            </Field>
            <Field label="시즌 (쉼표 구분)">
              <input className={inputClass} value={f.season ?? ""} onChange={(e) => setF("season", e.target.value)} />
            </Field>
            <Field label="소재">
              <input className={inputClass} value={f.material ?? ""} onChange={(e) => setF("material", e.target.value)} />
            </Field>
            <Field label="키워드 (쉼표 구분)">
              <input className={inputClass} value={f.keywords ?? ""} onChange={(e) => setF("keywords", e.target.value)} />
            </Field>
          </div>
          <div className="flex gap-2">
            <button type="button" className={primaryButtonClass} onClick={() => void saveManual()}>
              저장
            </button>
            <button type="button" className={buttonClass} onClick={() => setEdit(null)}>
              취소
            </button>
          </div>
        </Section>
      ) : null}
      <Table>
        <thead>
          <tr>
            <th>상품</th>
            <th>출처</th>
            <th>대분류 / 품목</th>
            <th>색상</th>
            <th>스타일 / 디자인</th>
            <th>시즌</th>
            <th>소재</th>
            <th>분석일</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={9} className="py-6 text-center text-muted-foreground">
                상품이 없습니다.
              </td>
            </tr>
          ) : null}
          {rows.map((r) => (
            <tr key={r.productId}>
              <td>
                <Link href={`/admin/products/${r.productId}/edit`} className="underline">
                  {r.productName}
                </Link>
                <p className="text-xs text-muted-foreground">
                  #{r.productId} · {r.status}
                </p>
              </td>
              <td>
                {r.source ? <Badge tone={r.source === "AI" ? "ok" : r.source === "MANUAL" ? "warn" : "muted"}>{FEATURE_SOURCE_LABELS[r.source] ?? r.source}</Badge> : <Badge>미생성</Badge>}
                {r.aiProvider ? <span className="ml-1 text-xs text-muted-foreground">{r.aiProvider}</span> : null}
              </td>
              <td>
                {r.group ? GROUP_LABELS[r.group] ?? r.group : "-"} / {r.itemType ?? "-"}
              </td>
              <td className="text-xs">
                {(r.colors ?? []).join(", ") || "-"}
                {r.colorTone ? <span className="text-muted-foreground"> ({r.colorTone})</span> : null}
              </td>
              <td className="max-w-[14rem] text-xs">
                {[...(r.styles ?? []), ...(r.designs ?? [])].join(", ") || "-"}
              </td>
              <td className="text-xs">{(r.seasons ?? []).join(", ") || "-"}</td>
              <td className="text-xs">{r.material ?? "-"}</td>
              <td className="whitespace-nowrap text-xs">{dateTime(r.analyzedAt)}</td>
              <td className="whitespace-nowrap">
                <button type="button" className="mr-2 text-xs underline disabled:opacity-50" disabled={busy === r.productId} onClick={() => void analyze(r.productId)}>
                  {busy === r.productId ? "분석 중..." : "AI 분석"}
                </button>
                <button
                  type="button"
                  className="text-xs underline"
                  onClick={() =>
                    setEdit({
                      productId: r.productId,
                      form: {
                        ...EMPTY_FEATURE,
                        categoryGroup: r.group,
                        itemType: r.itemType,
                        colors: (r.colors ?? []).join(", ") || null,
                        colorTone: r.colorTone,
                        styleTags: (r.styles ?? []).join(", ") || null,
                        designTags: (r.designs ?? []).join(", ") || null,
                        season: (r.seasons ?? []).join(", ") || null,
                        material: r.material,
                      },
                    })
                  }
                >
                  수동 입력
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </Table>
      <p className="text-xs text-muted-foreground">
        AI 분석은 상품당 1회 호출되며 같은 이미지는 캐시를 재사용합니다. 무료 한도 초과 시 유료 AI는 승인한 경우에만 호출됩니다.
      </p>
    </>
  );
}
