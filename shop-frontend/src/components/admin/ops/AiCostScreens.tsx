"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  adminJson,
  AI_MODE_LABELS,
  type AiCostUsage,
  type AiOverrides,
  type AiSettingsView,
  BILLING_LABELS,
  dateTime,
  errorMessage,
  num,
  postJson,
  PROVIDER_LABELS,
  usd,
} from "@/features/admin/ops";
import {
  Badge,
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

function thisMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

export function AiUsageClient() {
  const [data, setData] = useState<AiCostUsage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);
  const [billing, setBilling] = useState({ provider: "OPENAI", billingMonth: thisMonth(), amountUsd: "", memo: "" });
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setData(await adminJson<AiCostUsage>("/admin/ai/cost-usage"));
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function addBilling() {
    const amount = Number(billing.amountUsd);
    if (!billing.amountUsd.trim() || !Number.isFinite(amount) || amount < 0) {
      setError("실제 청구 금액(USD)을 숫자로 입력해 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    setSaved(null);
    try {
      await postJson("/admin/ai/billing", { ...billing, amountUsd: amount, memo: billing.memo.trim() || null });
      setSaved("실제 청구 금액을 기록했습니다.");
      setBilling((b) => ({ ...b, amountUsd: "", memo: "" }));
      void load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader
        title="AI 사용량 관리"
        description="외부 AI 호출 횟수·토큰·예상 비용과 무료 한도 상태를 확인합니다. API 키는 서버 환경변수로만 관리되며 화면에 표시되지 않습니다."
        actions={
          <>
            <Link href="/admin/ai/settings" className={buttonClass}>
              AI 환경설정
            </Link>
            <button type="button" className={buttonClass} onClick={() => void load()}>
              새로고침
            </button>
          </>
        }
      />
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {saved ? <Notice tone="ok">{saved}</Notice> : null}
      {data ? (
        <>
          <WarningList items={data.alerts} />
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard label="AI 모드" value={data.mode} sub={AI_MODE_LABELS[data.mode]} />
            <StatCard
              label="유료 API 사용"
              value={data.paidApiEnabled ? "사용 가능 (승인 시)" : "사용 안 함"}
              tone={data.paidApiEnabled ? "warn" : "ok"}
              sub={data.paidFallbackEnabled ? "자동 전환 켜짐 — 승인 없이 호출될 수 있음" : "자동 전환 꺼짐 — 관리자 승인 필요"}
            />
            <StatCard
              label="오늘 외부 AI 요청"
              value={`${num(data.dailyRequestsUsed)} / ${num(data.dailyRequestLimit)}회`}
              tone={data.dailyLimitReached ? "danger" : undefined}
              sub={data.dailyLimitReached ? "일일 한도 도달" : `캐시 ${data.cacheEnabled ? "사용" : "사용 안 함"}`}
            />
            <StatCard
              label={`${data.budget.month} 예산`}
              value={`${usd(data.budget.spentUsd, 2)} / ${usd(data.budget.budgetUsd, 2)}`}
              tone={data.budget.exceeded ? "danger" : undefined}
              sub={`남은 예산 ${usd(data.budget.remainingUsd, 2)} · 예약 ${usd(data.budget.reservedUsd, 2)} · 차단 ${num(data.budget.blockedCount)}회`}
            />
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <StatCard label="이번 달 예상 비용 (추정)" value={usd(data.monthEstimatedCostUsd)} sub="토큰 수 × 설정 단가" />
            <StatCard label="이번 달 실제 청구 금액" value={usd(data.monthActualBilledUsd, 2)} sub="관리자가 입력한 실제 청구액 (추정과 별도)" />
          </div>

          <Section title="제공자별 사용량">
            <Table>
              <thead>
                <tr>
                  <th>제공자 / 모델</th>
                  <th>과금</th>
                  <th>상태</th>
                  <th className="text-right">오늘 호출</th>
                  <th className="text-right">오늘 실패</th>
                  <th className="text-right">오늘 캐시</th>
                  <th className="text-right">오늘 차단</th>
                  <th className="text-right">오늘 토큰 (입/출)</th>
                  <th className="text-right">오늘 비용</th>
                  <th className="text-right">월 호출</th>
                  <th className="text-right">월 캐시</th>
                  <th className="text-right">월 토큰 (입/출)</th>
                  <th className="text-right">월 비용</th>
                  <th className="text-right">누적 비용</th>
                  <th>무료 한도</th>
                </tr>
              </thead>
              <tbody>
                {data.providers.map((p) => (
                  <tr key={p.provider}>
                    <td>
                      <span className="font-medium">{PROVIDER_LABELS[p.provider] ?? p.provider}</span>{" "}
                      <span className="text-xs text-muted-foreground">{p.model ?? ""}</span>
                    </td>
                    <td>
                      <Badge tone={p.billing === "PAID" ? "warn" : "muted"}>{BILLING_LABELS[p.billing] ?? p.billing}</Badge>
                    </td>
                    <td>
                      {p.enabled ? (p.keyConfigured ? <Badge tone="ok">사용</Badge> : <Badge tone="warn">키 미설정</Badge>) : <Badge>꺼짐</Badge>}
                    </td>
                    <td className="text-right tabular-nums">{num(p.todayCalls)}</td>
                    <td className="text-right tabular-nums">{num(p.todayFailures)}</td>
                    <td className="text-right tabular-nums">{num(p.todayCacheHits)}</td>
                    <td className="text-right tabular-nums">{num(p.todayBlocked)}</td>
                    <td className="text-right tabular-nums">
                      {num(p.todayInputTokens)} / {num(p.todayOutputTokens)}
                    </td>
                    <td className="text-right tabular-nums">{usd(p.todayEstimatedCostUsd)}</td>
                    <td className="text-right tabular-nums">{num(p.monthCalls)}</td>
                    <td className="text-right tabular-nums">{num(p.monthCacheHits)}</td>
                    <td className="text-right tabular-nums">
                      {num(p.monthInputTokens)} / {num(p.monthOutputTokens)}
                    </td>
                    <td className="text-right tabular-nums">{usd(p.monthEstimatedCostUsd)}</td>
                    <td className="text-right tabular-nums">{usd(p.totalEstimatedCostUsd)}</td>
                    <td>{p.quotaExhaustedToday ? <Badge tone="danger">오늘 초과</Badge> : p.provider === "GEMINI" ? <Badge tone="ok">여유</Badge> : "-"}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <p className="text-xs text-muted-foreground">{data.note}</p>
          </Section>

          <Section title="실제 청구 금액 기록">
            <div className="grid gap-3 sm:grid-cols-5">
              <Field label="제공업체">
                <select className={inputClass} value={billing.provider} onChange={(e) => setBilling({ ...billing, provider: e.target.value })}>
                  <option value="OPENAI">OpenAI</option>
                  <option value="GEMINI">Gemini</option>
                </select>
              </Field>
              <Field label="청구 월 (YYYY-MM)">
                <input className={inputClass} value={billing.billingMonth} onChange={(e) => setBilling({ ...billing, billingMonth: e.target.value })} />
              </Field>
              <Field label="금액 (USD)">
                <input className={inputClass} inputMode="decimal" value={billing.amountUsd} onChange={(e) => setBilling({ ...billing, amountUsd: e.target.value })} />
              </Field>
              <Field label="메모">
                <input className={inputClass} maxLength={300} value={billing.memo} onChange={(e) => setBilling({ ...billing, memo: e.target.value })} />
              </Field>
              <div className="flex items-end">
                <button type="button" className={primaryButtonClass} disabled={busy} onClick={() => void addBilling()}>
                  기록
                </button>
              </div>
            </div>
            <Table>
              <thead>
                <tr>
                  <th>청구 월</th>
                  <th>제공업체</th>
                  <th className="text-right">금액</th>
                  <th>메모</th>
                  <th>기록일</th>
                </tr>
              </thead>
              <tbody>
                {data.billingRecords.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="py-6 text-center text-muted-foreground">
                      기록된 실제 청구 금액이 없습니다.
                    </td>
                  </tr>
                ) : null}
                {data.billingRecords.map((r) => (
                  <tr key={r.id}>
                    <td>{r.billingMonth}</td>
                    <td>{PROVIDER_LABELS[r.provider] ?? r.provider}</td>
                    <td className="text-right tabular-nums">{usd(r.amountUsd, 2)}</td>
                    <td className="text-xs">{r.memo ?? ""}</td>
                    <td className="whitespace-nowrap">{dateTime(r.recordedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </Section>
        </>
      ) : null}
    </>
  );
}

type Tri = "" | "true" | "false";

function tri(value: boolean | null): Tri {
  return value === null ? "" : value ? "true" : "false";
}

function fromTri(value: Tri): boolean | null {
  return value === "" ? null : value === "true";
}

const BOOL_FIELDS: { key: keyof AiOverrides; label: string; hint: string }[] = [
  { key: "localOcrEnabled", label: "로컬 OCR (PaddleOCR)", hint: "AI_LOCAL_OCR_ENABLED — 영수증을 무료로 먼저 읽습니다." },
  { key: "geminiEnabled", label: "Gemini (무료 등급)", hint: "GEMINI_API_KEY 필요 — 무료 한도 초과 시 자동으로 멈춥니다." },
  { key: "openaiEnabled", label: "OpenAI (유료)", hint: "OPENAI_ENABLED — 켜도 승인한 요청에서만 호출됩니다." },
  { key: "paidFallbackEnabled", label: "유료 자동 전환", hint: "AI_PAID_FALLBACK_ENABLED — 켜면 승인 없이 OpenAI로 넘어갈 수 있습니다 (권장: 끔)." },
  { key: "cacheEnabled", label: "분석 결과 캐시", hint: "AI_CACHE_ENABLED — 같은 이미지·프롬프트는 다시 호출하지 않습니다." },
];

export function AiSettingsClient() {
  const [view, setView] = useState<AiSettingsView | null>(null);
  const [form, setForm] = useState<AiOverrides | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    adminJson<AiSettingsView>("/admin/ai/settings")
      .then((v) => {
        setView(v);
        setForm(v.overrides);
      })
      .catch((e) => setError(errorMessage(e)));
  }, []);

  async function save(body: AiOverrides) {
    setBusy(true);
    setError(null);
    setSaved(null);
    try {
      const v = await postJson<AiSettingsView>("/admin/ai/settings", body, "PUT");
      setView(v);
      setForm(v.overrides);
      setSaved("저장했습니다. 다음 분석 요청부터 적용됩니다.");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const env = view?.environmentDefaults ?? {};
  const eff = view?.effective;

  return (
    <>
      <PageHeader
        title="AI 환경설정"
        description="비워 두면 서버 환경변수(.env) 값을 따릅니다. API 키는 이 화면이나 DB에 저장하지 않으며, 서버 환경변수로만 설정합니다."
        actions={
          <Link href="/admin/ai/usage" className={buttonClass}>
            AI 사용량 관리
          </Link>
        }
      />
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {saved ? <Notice tone="ok">{saved}</Notice> : null}
      {view && eff ? (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard label="현재 모드" value={eff.mode} sub={AI_MODE_LABELS[eff.mode]} />
          <StatCard
            label="로컬 OCR 서버"
            value={eff.localOcrEnabled ? (view.localOcrHealthy ? "정상" : "응답 없음") : "꺼짐"}
            tone={eff.localOcrEnabled ? (view.localOcrHealthy ? "ok" : "warn") : undefined}
          />
          <StatCard
            label="Gemini"
            value={eff.geminiKeyConfigured ? "키 설정됨" : "키 미설정"}
            tone={eff.geminiKeyConfigured ? "ok" : "warn"}
            sub={`${eff.geminiModel ?? "-"} · ${eff.geminiFreeTier ? "무료 등급" : "유료 등급"}`}
          />
          <StatCard
            label="OpenAI"
            value={eff.openaiKeyConfigured ? "키 설정됨" : "키 미설정"}
            tone={eff.openaiEnabled && eff.openaiKeyConfigured ? "warn" : undefined}
            sub={`${eff.openaiModel ?? "-"} · ${eff.openaiEnabled ? "사용 허용(승인 필요)" : "사용 안 함"}`}
          />
        </div>
      ) : null}
      {view && form ? (
        <Section title="설정 (DB 재정의값)">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="AI 모드" hint={`환경변수 기본값: ${String(env.mode ?? "-")}`}>
              <select className={inputClass} value={form.mode ?? ""} onChange={(e) => setForm({ ...form, mode: e.target.value || null })}>
                <option value="">기본값 사용</option>
                {view.modes.map((m) => (
                  <option key={m} value={m}>
                    {m} — {AI_MODE_LABELS[m] ?? m}
                  </option>
                ))}
              </select>
            </Field>
            {BOOL_FIELDS.map((f) => (
              <Field key={f.key} label={f.label} hint={`${f.hint} (기본값: ${env[f.key] ? "켜짐" : "꺼짐"})`}>
                <select
                  className={inputClass}
                  value={tri(form[f.key] as boolean | null)}
                  onChange={(e) => setForm({ ...form, [f.key]: fromTri(e.target.value as Tri) })}
                >
                  <option value="">기본값 사용</option>
                  <option value="true">켜짐</option>
                  <option value="false">꺼짐</option>
                </select>
              </Field>
            ))}
            <Field label="일일 외부 AI 요청 한도" hint={`AI_DAILY_REQUEST_LIMIT (기본값: ${String(env.dailyRequestLimit ?? "-")}회)`}>
              <input
                className={inputClass}
                inputMode="numeric"
                placeholder="기본값 사용"
                value={form.dailyRequestLimit ?? ""}
                onChange={(e) => setForm({ ...form, dailyRequestLimit: e.target.value === "" ? null : Number(e.target.value) })}
              />
            </Field>
            <Field label="월 유료 AI 예산 (USD)" hint={`AI_MONTHLY_BUDGET_USD (기본값: $${String(env.monthlyBudgetUsd ?? "-")})`}>
              <input
                className={inputClass}
                inputMode="decimal"
                placeholder="기본값 사용"
                value={form.monthlyBudgetUsd ?? ""}
                onChange={(e) => setForm({ ...form, monthlyBudgetUsd: e.target.value === "" ? null : Number(e.target.value) })}
              />
            </Field>
          </div>
          {form.paidFallbackEnabled ? (
            <Notice tone="warn">유료 자동 전환을 켜면 Gemini 실패·한도 초과 시 관리자 승인 없이 OpenAI가 호출되어 비용이 발생할 수 있습니다.</Notice>
          ) : null}
          <div className="flex gap-2">
            <button type="button" className={primaryButtonClass} disabled={busy} onClick={() => void save(form)}>
              저장
            </button>
            <button
              type="button"
              className={buttonClass}
              disabled={busy}
              onClick={() =>
                void save({
                  mode: null,
                  localOcrEnabled: null,
                  geminiEnabled: null,
                  openaiEnabled: null,
                  paidFallbackEnabled: null,
                  cacheEnabled: null,
                  dailyRequestLimit: null,
                  monthlyBudgetUsd: null,
                })
              }
            >
              모두 환경변수 기본값으로
            </button>
          </div>
        </Section>
      ) : null}
      <Section title="처리 순서 (FREE_FIRST)">
        <ol className="list-decimal space-y-1 pl-5 text-sm">
          <li>로컬 OCR + Java 규칙으로 영수증을 읽습니다 (비용 없음).</li>
          <li>결과가 불확실하거나 상품 이미지 분석이면 Gemini 무료 등급을 사용합니다.</li>
          <li>OpenAI는 관리자가 켜고, 요청마다 승인한 경우에만 사용합니다 (월 예산 한도 내).</li>
          <li>모두 사용할 수 없으면 수동 입력으로 진행합니다. 가격·마진·재고·통계는 항상 Java/SQL로만 계산합니다.</li>
        </ol>
      </Section>
    </>
  );
}
