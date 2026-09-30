"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError } from "@/lib/api/client";
import { formatKrw } from "@/lib/format";
import { cn } from "@/lib/utils";
import { formatDateTime } from "@/features/orders/status";
import {
  addExtraArea,
  allowOperationRetry,
  canAllowRetry,
  deleteExtraArea,
  getAdminShippingPolicy,
  getShippingIntegration,
  listDeliveryCompanies,
  listExtraAreas,
  listShippingOperations,
  listShippingProviders,
  OPERATION_FILTERS,
  OPERATION_STATUS_LABELS,
  OPERATION_TYPE_LABELS,
  PROVIDER_CAPABILITIES,
  setDeliveryCompanyEnabled,
  setExtraAreaEnabled,
  testShippingProvider,
  updateProviderCode,
  updateShippingPolicy,
  updateShippingProvider,
  type DeliveryCompany,
  type ExtraArea,
  type OperationFilter,
  type ShippingIntegration,
  type ShippingOperation,
  type ShippingPolicyInput,
  type ShippingProviderRow,
} from "@/features/admin/shipping";

const POLICY_FIELDS: { key: keyof ShippingPolicyInput; label: string; hint?: string }[] = [
  { key: "baseShippingFee", label: "기본 배송비" },
  { key: "freeShippingAmount", label: "무료배송 기준 금액", hint: "상품금액이 이 금액 이상이면 기본 배송비 무료" },
  { key: "jejuExtraFee", label: "제주 추가 배송비", hint: "지역별 추가요금을 따로 정하지 않은 제주 지역에 적용" },
  { key: "remoteAreaExtraFee", label: "도서산간 추가 배송비", hint: "지역별 추가요금을 따로 정하지 않은 도서산간 지역에 적용" },
  { key: "returnShippingFee", label: "반품 배송비", hint: "단순 변심 등 고객 사유 반품 시 환불액에서 차감" },
  { key: "exchangeShippingFee", label: "교환 배송비", hint: "고객 사유 교환 시 왕복 배송비 (교환 기능 오픈 시 사용)" },
];

const EMPTY_AREA = { areaType: "REMOTE" as "JEJU" | "REMOTE", areaName: "", postalCodeFrom: "", postalCodeTo: "", extraFee: "" };

const inputClass = "h-10 rounded-lg border border-border bg-surface px-3 text-sm";
const smallButton = "h-8 rounded-lg border border-border px-2.5 text-xs hover:bg-surface-soft disabled:opacity-50";

function errorMessage(e: unknown, fallback: string) {
  return e instanceof ApiError ? e.message : fallback;
}

export function ShippingSettingsClient() {
  const [policy, setPolicy] = useState<Record<keyof ShippingPolicyInput, string> | null>(null);
  const [areas, setAreas] = useState<ExtraArea[]>([]);
  const [companies, setCompanies] = useState<DeliveryCompany[]>([]);
  const [providers, setProviders] = useState<ShippingProviderRow[]>([]);
  const [integration, setIntegration] = useState<ShippingIntegration | null>(null);
  const [area, setArea] = useState(EMPTY_AREA);
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [p, a, c, pr, i] = await Promise.all([
        getAdminShippingPolicy(),
        listExtraAreas(),
        listDeliveryCompanies(),
        listShippingProviders(),
        getShippingIntegration(),
      ]);
      setPolicy({
        baseShippingFee: String(p.baseShippingFee),
        freeShippingAmount: String(p.freeShippingAmount),
        jejuExtraFee: String(p.jejuExtraFee),
        remoteAreaExtraFee: String(p.remoteAreaExtraFee),
        returnShippingFee: String(p.returnShippingFee),
        exchangeShippingFee: String(p.exchangeShippingFee ?? 0),
      });
      setAreas(a);
      setCompanies(c);
      setProviders(pr);
      setIntegration(i);
    } catch (e) {
      setError(errorMessage(e, "배송 설정을 불러오지 못했습니다."));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function run(key: string, action: () => Promise<unknown>, done: string) {
    setBusy(key);
    setNotice(null);
    setError(null);
    try {
      await action();
      setNotice(done);
      await load();
      return true;
    } catch (e) {
      setError(errorMessage(e, "저장에 실패했습니다."));
      return false;
    } finally {
      setBusy(null);
    }
  }

  function savePolicy() {
    if (!policy) return;
    const input = Object.fromEntries(POLICY_FIELDS.map(({ key }) => [key, Number(policy[key])])) as ShippingPolicyInput;
    if (Object.values(input).some((v) => !Number.isFinite(v) || v < 0 || !Number.isInteger(v))) {
      setError("금액은 0 이상의 원 단위 정수로 입력해 주세요.");
      return;
    }
    void run("policy", () => updateShippingPolicy(input), "배송비 정책을 저장했습니다. 이후 주문부터 적용됩니다.");
  }

  function submitArea() {
    const extraFee = area.extraFee.trim() === "" ? null : Number(area.extraFee);
    if (extraFee !== null && (!Number.isInteger(extraFee) || extraFee < 0)) {
      setError("추가요금은 0 이상의 원 단위 정수로 입력해 주세요.");
      return;
    }
    void run(
      "area",
      () =>
        addExtraArea({
          areaType: area.areaType,
          areaName: area.areaName.trim(),
          postalCodeFrom: area.postalCodeFrom,
          postalCodeTo: area.postalCodeTo || area.postalCodeFrom,
          extraFee,
        }),
      "지역을 추가했습니다.",
    ).then((ok) => {
      if (ok) setArea({ ...EMPTY_AREA, areaType: area.areaType });
    });
  }

  async function testConnection(provider: ShippingProviderRow) {
    setBusy(`test-${provider.code}`);
    setNotice(null);
    setError(null);
    try {
      const res = await testShippingProvider(provider.code);
      if (res.success) setNotice(`${provider.name}: ${res.message}`);
      else setError(`${provider.name}: ${res.message}`);
    } catch (e) {
      setError(errorMessage(e, "연결 테스트에 실패했습니다."));
    } finally {
      setBusy(null);
    }
  }

  const apiProviders = providers.filter((p) => p.code !== "MANUAL");

  return (
    <div className="flex flex-col gap-6">
      {notice ? (
        <p className="rounded-lg border border-brand/30 bg-brand-soft px-3 py-2 text-sm" role="status">
          {notice}
        </p>
      ) : null}
      {error ? (
        <p className="text-sm text-danger" role="alert">
          {error}
        </p>
      ) : null}

      <section className="rounded-xl border border-border bg-surface p-4 text-sm">
        <h2 className="mb-3 text-base font-semibold">배송비 정책</h2>
        {policy ? (
          <form
            className="flex flex-col gap-3"
            onSubmit={(e) => {
              e.preventDefault();
              savePolicy();
            }}
          >
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {POLICY_FIELDS.map((field) => (
                <label key={field.key} className="flex flex-col gap-1">
                  <span className="font-medium">{field.label} (원)</span>
                  <input
                    type="number"
                    min={0}
                    step={100}
                    inputMode="numeric"
                    className={inputClass}
                    value={policy[field.key]}
                    onChange={(e) => setPolicy((prev) => (prev ? { ...prev, [field.key]: e.target.value } : prev))}
                    required
                  />
                  {field.hint ? <span className="text-xs text-muted-foreground">{field.hint}</span> : null}
                </label>
              ))}
            </div>
            <p className="text-xs text-muted-foreground">
              예) 상품금액 {formatKrw(Number(policy.freeShippingAmount) || 0)}원 이상 무료배송, 미만{" "}
              {formatKrw(Number(policy.baseShippingFee) || 0)}원. 제주·도서산간 추가 배송비는 무료배송이어도 부과됩니다.
            </p>
            <div>
              <button
                type="submit"
                disabled={busy !== null}
                className="h-10 rounded-lg bg-brand px-5 text-sm font-medium text-white hover:bg-brand-hover disabled:opacity-60"
              >
                {busy === "policy" ? "저장 중..." : "정책 저장"}
              </button>
            </div>
          </form>
        ) : (
          <p className="text-muted-foreground">불러오는 중...</p>
        )}
      </section>

      <section className="rounded-xl border border-border bg-surface p-4 text-sm">
        <h2 className="mb-3 text-base font-semibold">제주 · 도서산간 지역 (우편번호 범위)</h2>
        <form
          className="mb-3 flex flex-wrap items-end gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            submitArea();
          }}
        >
          <select
            className={inputClass}
            value={area.areaType}
            onChange={(e) => setArea({ ...area, areaType: e.target.value as "JEJU" | "REMOTE" })}
            aria-label="지역 구분"
          >
            <option value="JEJU">제주</option>
            <option value="REMOTE">도서산간</option>
          </select>
          <input
            className={`${inputClass} w-40`}
            placeholder="지역명 (예: 울릉도)"
            maxLength={100}
            value={area.areaName}
            onChange={(e) => setArea({ ...area, areaName: e.target.value })}
            required
          />
          <input
            className={`${inputClass} w-28`}
            placeholder="시작 63000"
            pattern="\d{5}"
            value={area.postalCodeFrom}
            onChange={(e) => setArea({ ...area, postalCodeFrom: e.target.value })}
            required
          />
          <input
            className={`${inputClass} w-28`}
            placeholder="끝 (선택)"
            pattern="\d{5}"
            value={area.postalCodeTo}
            onChange={(e) => setArea({ ...area, postalCodeTo: e.target.value })}
          />
          <input
            className={`${inputClass} w-36`}
            type="number"
            min={0}
            step={100}
            placeholder="추가요금 (선택)"
            value={area.extraFee}
            onChange={(e) => setArea({ ...area, extraFee: e.target.value })}
          />
          <button
            type="submit"
            disabled={busy !== null}
            className="h-10 rounded-lg border border-border px-4 text-sm hover:bg-surface-soft disabled:opacity-60"
          >
            추가
          </button>
        </form>
        <p className="mb-2 text-xs text-muted-foreground">
          추가요금을 비워 두면 배송비 정책의 제주/도서산간 추가 배송비가 적용됩니다. 여러 범위에 걸치면 제주가 우선합니다.
        </p>
        <div className="max-h-80 overflow-auto rounded-lg border border-border">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-surface-soft text-muted-foreground">
              <tr>
                <th className="px-3 py-2">구분</th>
                <th className="px-3 py-2">지역명</th>
                <th className="px-3 py-2">우편번호</th>
                <th className="px-3 py-2">추가요금</th>
                <th className="px-3 py-2">사용</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {areas.map((a) => (
                <tr key={a.areaId} className={cn("border-t border-border", !a.enabled && "text-muted-foreground")}>
                  <td className="px-3 py-2">{a.areaType === "JEJU" ? "제주" : "도서산간"}</td>
                  <td className="px-3 py-2">
                    {a.areaName}
                    {a.source === "MANUAL" ? <span className="ml-1 text-xs text-muted-foreground">(직접 추가)</span> : null}
                  </td>
                  <td className="px-3 py-2 tabular-nums">
                    {a.postalCodeFrom}
                    {a.postalCodeTo !== a.postalCodeFrom ? ` ~ ${a.postalCodeTo}` : ""}
                  </td>
                  <td className="px-3 py-2 tabular-nums">
                    {formatKrw(a.effectiveFee)}원
                    {a.extraFee == null ? <span className="ml-1 text-xs text-muted-foreground">(정책 기본)</span> : null}
                  </td>
                  <td className="px-3 py-2">
                    <input
                      type="checkbox"
                      aria-label={`${a.areaName} 사용`}
                      checked={a.enabled}
                      disabled={busy !== null}
                      onChange={(e) =>
                        void run(
                          `area-toggle-${a.areaId}`,
                          () => setExtraAreaEnabled(a.areaId, e.target.checked),
                          `${a.areaName}을(를) ${e.target.checked ? "사용" : "사용 안 함"}으로 변경했습니다.`,
                        )
                      }
                    />
                  </td>
                  <td className="px-3 py-2 text-right">
                    <button
                      type="button"
                      disabled={busy !== null}
                      className="text-xs text-danger hover:underline disabled:opacity-50"
                      onClick={() => {
                        if (!window.confirm("이 지역을 삭제할까요? 잠시 제외하려면 ‘사용’을 끄세요.")) return;
                        void run(`area-${a.areaId}`, () => deleteExtraArea(a.areaId), "지역을 삭제했습니다.");
                      }}
                    >
                      삭제
                    </button>
                  </td>
                </tr>
              ))}
              {areas.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-3 py-6 text-center text-muted-foreground">
                    등록된 지역이 없습니다.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>

      <section className="rounded-xl border border-border bg-surface p-4 text-sm">
        <h2 className="mb-1 text-base font-semibold">택배사</h2>
        <p className="mb-3 text-xs text-muted-foreground">
          사용 중인 택배사만 송장 등록 화면에 표시됩니다. 업체 코드는 외부 API 업체가 이 택배사를 부르는 코드이며, 비우면
          해당 업체로 이 택배사를 조회·발급하지 않습니다.
        </p>
        <div className="overflow-auto rounded-lg border border-border">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-surface-soft text-muted-foreground">
              <tr>
                <th className="px-3 py-2">택배사</th>
                <th className="px-3 py-2">사용</th>
                {apiProviders.map((p) => (
                  <th key={p.code} className="px-3 py-2">
                    {p.name} 코드
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {companies.map((c) => (
                <tr key={c.code} className="border-t border-border">
                  <td className="px-3 py-2">
                    {c.companyName} <span className="text-xs text-muted-foreground">({c.code})</span>
                  </td>
                  <td className="px-3 py-2">
                    <input
                      type="checkbox"
                      aria-label={`${c.companyName} 사용`}
                      checked={c.enabled}
                      disabled={busy !== null}
                      onChange={(e) =>
                        void run(
                          `company-${c.code}`,
                          () => setDeliveryCompanyEnabled(c.code, e.target.checked),
                          `${c.companyName}을(를) ${e.target.checked ? "사용" : "사용 안 함"}으로 변경했습니다.`,
                        )
                      }
                    />
                  </td>
                  {apiProviders.map((p) => (
                    <td key={p.code} className="px-3 py-2">
                      <ProviderCodeInput
                        key={`${c.code}-${p.code}-${c.providerCodes[p.code] ?? ""}`}
                        label={`${c.companyName} ${p.name} 코드`}
                        value={c.providerCodes[p.code] ?? ""}
                        disabled={busy !== null}
                        onSave={(next) =>
                          void run(
                            `code-${c.code}-${p.code}`,
                            () => updateProviderCode(c.code, p.code, next),
                            next
                              ? `${c.companyName}의 ${p.name} 코드를 ${next}(으)로 저장했습니다.`
                              : `${c.companyName}의 ${p.name} 코드를 삭제했습니다.`,
                          )
                        }
                      />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="rounded-xl border border-border bg-surface p-4 text-sm">
        <h2 className="mb-1 text-base font-semibold">외부 배송 API 업체</h2>
        <p className="mb-3 text-xs text-muted-foreground">
          API 키는 서버 환경변수로만 관리되며 화면과 DB에 저장되지 않습니다. 기능별로 켜진 업체 중 우선순위가 높은 업체가
          사용되고, 사용할 수 있는 업체가 없으면 수동 처리로 동작합니다.
        </p>
        <div className="overflow-auto rounded-lg border border-border">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-surface-soft text-muted-foreground">
              <tr>
                <th className="px-3 py-2">업체</th>
                <th className="px-3 py-2">API 키</th>
                <th className="px-3 py-2">사용</th>
                {PROVIDER_CAPABILITIES.map((cap) => (
                  <th key={cap.key} className="px-3 py-2">
                    {cap.label}
                  </th>
                ))}
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {providers.map((p) => (
                <tr key={p.code} className="border-t border-border">
                  <td className="px-3 py-2">
                    {p.name}
                    {p.preferred ? <span className="ml-1 text-xs text-brand">(환경변수 지정)</span> : null}
                    {p.activeCapabilities.length > 0 ? (
                      <p className="text-xs text-muted-foreground">
                        사용 중:{" "}
                        {p.activeCapabilities
                          .map((cap) => PROVIDER_CAPABILITIES.find((c) => c.capability === cap)?.label ?? cap)
                          .join(", ")}
                      </p>
                    ) : null}
                  </td>
                  <td className="px-3 py-2 text-xs">
                    {p.code === "MANUAL" ? "-" : p.configured ? "설정됨" : <span className="text-accent">미설정</span>}
                  </td>
                  <td className="px-3 py-2">
                    <input
                      type="checkbox"
                      aria-label={`${p.name} 사용`}
                      checked={p.enabled}
                      disabled={busy !== null || p.code === "MANUAL"}
                      onChange={(e) =>
                        void run(
                          `provider-${p.code}`,
                          () => updateShippingProvider(p.code, { enabled: e.target.checked }),
                          `${p.name}을(를) ${e.target.checked ? "사용" : "사용 안 함"}으로 변경했습니다.`,
                        )
                      }
                    />
                  </td>
                  {PROVIDER_CAPABILITIES.map((cap) => {
                    const supported = p.supportedCapabilities.includes(cap.capability);
                    return (
                      <td key={cap.key} className="px-3 py-2">
                        {supported ? (
                          <input
                            type="checkbox"
                            aria-label={`${p.name} ${cap.label}`}
                            checked={p[cap.key]}
                            disabled={busy !== null}
                            onChange={(e) =>
                              void run(
                                `provider-${p.code}-${cap.key}`,
                                () => updateShippingProvider(p.code, { [cap.key]: e.target.checked }),
                                `${p.name} ${cap.label}을(를) ${e.target.checked ? "켰" : "껐"}습니다.`,
                              )
                            }
                          />
                        ) : (
                          <span className="text-xs text-muted-foreground">미지원</span>
                        )}
                      </td>
                    );
                  })}
                  <td className="px-3 py-2 text-right">
                    {p.code !== "MANUAL" ? (
                      <button
                        type="button"
                        className={smallButton}
                        disabled={busy !== null}
                        onClick={() => void testConnection(p)}
                      >
                        {busy === `test-${p.code}` ? "확인 중..." : "연결 테스트"}
                      </button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {integration ? (
          <dl className="mt-4 grid grid-cols-[160px_1fr] gap-y-1.5">
            <dt className="text-muted-foreground">환경변수 지정 업체</dt>
            <dd>{integration.requestedProvider}</dd>
            <dt className="text-muted-foreground">배송조회</dt>
            <dd>
              {integration.activeProvider}
              {integration.externalTracking ? (
                <span className="ml-2 text-xs text-brand">실시간 조회 사용</span>
              ) : (
                <span className="ml-2 text-xs text-accent">수동 모드 (API 키 미설정 또는 비활성)</span>
              )}
            </dd>
            <dt className="text-muted-foreground">자동 조회 스케줄러</dt>
            <dd>
              {integration.schedulerEnabled ? `사용 (${integration.schedulerCron})` : "사용 안 함"} · 캐시{" "}
              {integration.cacheMinutes}분
            </dd>
            <dt className="text-muted-foreground">운송장 발급</dt>
            <dd>{integration.waybillProvider ?? "사용 가능한 업체 없음"}</dd>
            <dt className="text-muted-foreground">집하 요청</dt>
            <dd>{integration.pickupService ?? "-"}</dd>
            <dt className="text-muted-foreground">반품 수거</dt>
            <dd>{integration.returnPickupProvider ?? "-"}</dd>
          </dl>
        ) : null}
      </section>

      <OperationLog />
    </div>
  );
}

function ProviderCodeInput({
  label,
  value,
  disabled,
  onSave,
}: {
  label: string;
  value: string;
  disabled: boolean;
  onSave: (next: string) => void;
}) {
  const [draft, setDraft] = useState(value);
  const dirty = draft.trim() !== value;
  return (
    <form
      className="flex items-center gap-1"
      onSubmit={(e) => {
        e.preventDefault();
        if (dirty) onSave(draft.trim());
      }}
    >
      <input
        className="h-8 w-24 rounded-md border border-border bg-surface px-2 text-xs"
        aria-label={label}
        maxLength={30}
        pattern="[A-Za-z0-9_\-]*"
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
      />
      {dirty ? (
        <button type="submit" className={smallButton} disabled={disabled}>
          저장
        </button>
      ) : null}
    </form>
  );
}

function OperationLog() {
  const [filter, setFilter] = useState<OperationFilter>("ATTENTION");
  const [rows, setRows] = useState<ShippingOperation[] | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (nextFilter: OperationFilter, nextPage: number) => {
    try {
      const data = await listShippingOperations(nextFilter, nextPage);
      setRows(data.content);
      setPage(data.page);
      setTotalPages(data.totalPages);
      setError(null);
    } catch (e) {
      setError(errorMessage(e, "API 호출 이력을 불러오지 못했습니다."));
    }
  }, []);

  useEffect(() => {
    void load(filter, 0);
  }, [filter, load]);

  async function allowRetry(op: ShippingOperation) {
    if (
      !window.confirm(
        "업체 관리 화면에서 이 요청이 처리되지 않았음을 확인했나요?\n재시도를 허용하면 다음 요청 때 업체를 다시 호출합니다.",
      )
    ) {
      return;
    }
    setBusyId(op.operationId);
    setNotice(null);
    setError(null);
    try {
      await allowOperationRetry(op.operationId);
      setNotice("재시도를 허용했습니다. 주문/반품 화면에서 같은 작업을 다시 실행해 주세요.");
      await load(filter, page);
    } catch (e) {
      setError(errorMessage(e, "처리에 실패했습니다."));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section className="rounded-xl border border-border bg-surface p-4 text-sm">
      <h2 className="mb-1 text-base font-semibold">배송 API 호출 이력</h2>
      <p className="mb-3 text-xs text-muted-foreground">
        업체 응답을 받지 못한 요청은 ‘결과 확인 필요’로 남으며, 중복 발급을 막기 위해 자동으로 다시 호출하지 않습니다.
        업체 쪽에서 처리되지 않은 것을 확인한 뒤 재시도를 허용하세요. 개인정보와 API 키는 기록하지 않습니다.
      </p>
      <div className="mb-3 flex flex-wrap gap-1">
        {OPERATION_FILTERS.map((f) => (
          <button
            key={f.value}
            type="button"
            onClick={() => {
              setRows(null);
              setFilter(f.value);
            }}
            className={cn(
              "rounded-md px-3 py-1.5 text-xs transition-colors",
              filter === f.value
                ? "bg-brand text-white"
                : "border border-border text-muted-foreground hover:bg-surface-soft hover:text-foreground",
            )}
          >
            {f.label}
          </button>
        ))}
      </div>
      {notice ? (
        <p className="mb-2 rounded-lg border border-brand/30 bg-brand-soft px-3 py-2" role="status">
          {notice}
        </p>
      ) : null}
      {error ? (
        <p className="mb-2 text-danger" role="alert">
          {error}
        </p>
      ) : null}
      {rows === null ? (
        <p className="text-muted-foreground">불러오는 중...</p>
      ) : rows.length === 0 ? (
        <p className="rounded-lg border border-dashed border-border p-6 text-center text-muted-foreground">
          해당하는 호출 이력이 없습니다.
        </p>
      ) : (
        <div className="overflow-auto rounded-lg border border-border">
          <table className="min-w-full text-left text-xs">
            <thead className="bg-surface-soft text-muted-foreground">
              <tr>
                <th className="px-3 py-2">요청 시각</th>
                <th className="px-3 py-2">업체</th>
                <th className="px-3 py-2">작업</th>
                <th className="px-3 py-2">대상</th>
                <th className="px-3 py-2">상태</th>
                <th className="px-3 py-2">내용</th>
                <th className="px-3 py-2">시도</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {rows.map((op) => (
                <tr key={op.operationId} className={cn("border-t border-border", op.needsAttention && "bg-accent-soft/40")}>
                  <td className="px-3 py-2 tabular-nums">{formatDateTime(op.requestedAt)}</td>
                  <td className="px-3 py-2">{op.providerCode}</td>
                  <td className="px-3 py-2">{OPERATION_TYPE_LABELS[op.operationType] ?? op.operationType}</td>
                  <td className="px-3 py-2 tabular-nums">
                    {op.returnRequestId ? `반품 #${op.returnRequestId}` : op.orderId ? `주문 #${op.orderId}` : "-"}
                  </td>
                  <td className="px-3 py-2">
                    {OPERATION_STATUS_LABELS[op.status] ?? op.status}
                    {op.status === "SUCCEEDED" && op.needsAttention ? (
                      <span className="block text-accent">저장 대기</span>
                    ) : null}
                  </td>
                  <td className="max-w-xs px-3 py-2">
                    {op.errorMessage ?? op.externalReference ?? "-"}
                    {op.httpStatus ? <span className="ml-1 text-muted-foreground">(HTTP {op.httpStatus})</span> : null}
                  </td>
                  <td className="px-3 py-2 tabular-nums">{op.attemptCount}</td>
                  <td className="px-3 py-2 text-right">
                    {canAllowRetry(op) ? (
                      <button
                        type="button"
                        className={smallButton}
                        disabled={busyId !== null}
                        onClick={() => void allowRetry(op)}
                      >
                        재시도 허용
                      </button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {totalPages > 1 ? (
        <div className="mt-3 flex items-center justify-center gap-3">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => void load(filter, page - 1)}
            className="rounded-md border border-border px-3 py-1.5 disabled:opacity-40"
          >
            이전
          </button>
          <span className="tabular-nums text-muted-foreground">
            {page + 1} / {totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= totalPages}
            onClick={() => void load(filter, page + 1)}
            className="rounded-md border border-border px-3 py-1.5 disabled:opacity-40"
          >
            다음
          </button>
        </div>
      ) : null}
    </section>
  );
}
