"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError } from "@/lib/api/client";
import { formatKrw } from "@/lib/format";
import {
  addExtraArea,
  deleteExtraArea,
  getAdminShippingPolicy,
  getShippingIntegration,
  listDeliveryCompanies,
  listExtraAreas,
  setDeliveryCompanyEnabled,
  updateShippingPolicy,
  type DeliveryCompany,
  type ExtraArea,
  type ShippingIntegration,
  type ShippingPolicyInput,
} from "@/features/admin/shipping";

const POLICY_FIELDS: { key: keyof ShippingPolicyInput; label: string; hint?: string }[] = [
  { key: "baseShippingFee", label: "기본 배송비" },
  { key: "freeShippingAmount", label: "무료배송 기준 금액", hint: "상품금액이 이 금액 이상이면 기본 배송비 무료" },
  { key: "jejuExtraFee", label: "제주 추가 배송비" },
  { key: "remoteAreaExtraFee", label: "도서산간 추가 배송비" },
  { key: "returnShippingFee", label: "반품 배송비", hint: "단순 변심 등 고객 사유 반품 시 환불액에서 차감" },
];

const inputClass = "h-10 rounded-lg border border-border bg-surface px-3 text-sm";

function errorMessage(e: unknown, fallback: string) {
  return e instanceof ApiError ? e.message : fallback;
}

export function ShippingSettingsClient() {
  const [policy, setPolicy] = useState<Record<keyof ShippingPolicyInput, string> | null>(null);
  const [areas, setAreas] = useState<ExtraArea[]>([]);
  const [companies, setCompanies] = useState<DeliveryCompany[]>([]);
  const [integration, setIntegration] = useState<ShippingIntegration | null>(null);
  const [area, setArea] = useState({ areaType: "REMOTE" as "JEJU" | "REMOTE", postcodeFrom: "", postcodeTo: "", note: "" });
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [p, a, c, i] = await Promise.all([
        getAdminShippingPolicy(),
        listExtraAreas(),
        listDeliveryCompanies(),
        getShippingIntegration(),
      ]);
      setPolicy({
        baseShippingFee: String(p.baseShippingFee),
        freeShippingAmount: String(p.freeShippingAmount),
        jejuExtraFee: String(p.jejuExtraFee),
        remoteAreaExtraFee: String(p.remoteAreaExtraFee),
        returnShippingFee: String(p.returnShippingFee),
      });
      setAreas(a);
      setCompanies(c);
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
    const input = Object.fromEntries(
      POLICY_FIELDS.map(({ key }) => [key, Number(policy[key])]),
    ) as ShippingPolicyInput;
    if (Object.values(input).some((v) => !Number.isFinite(v) || v < 0)) {
      setError("금액은 0 이상의 숫자로 입력해 주세요.");
      return;
    }
    void run("policy", () => updateShippingPolicy(input), "배송비 정책을 저장했습니다. 이후 주문부터 적용됩니다.");
  }

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
            void run(
              "area",
              () => addExtraArea({ ...area, postcodeTo: area.postcodeTo || area.postcodeFrom, note: area.note || undefined }),
              "지역을 추가했습니다.",
            ).then((ok) => {
              if (ok) setArea({ ...area, postcodeFrom: "", postcodeTo: "", note: "" });
            });
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
            className={`${inputClass} w-28`}
            placeholder="시작 63000"
            pattern="\d{5}"
            value={area.postcodeFrom}
            onChange={(e) => setArea({ ...area, postcodeFrom: e.target.value })}
            required
          />
          <input
            className={`${inputClass} w-28`}
            placeholder="끝 (선택)"
            pattern="\d{5}"
            value={area.postcodeTo}
            onChange={(e) => setArea({ ...area, postcodeTo: e.target.value })}
          />
          <input
            className={`${inputClass} w-48`}
            placeholder="메모 (예: 울릉도)"
            maxLength={100}
            value={area.note}
            onChange={(e) => setArea({ ...area, note: e.target.value })}
          />
          <button
            type="submit"
            disabled={busy !== null}
            className="h-10 rounded-lg border border-border px-4 text-sm hover:bg-surface-soft disabled:opacity-60"
          >
            추가
          </button>
        </form>
        <div className="max-h-72 overflow-auto rounded-lg border border-border">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-surface-soft text-muted-foreground">
              <tr>
                <th className="px-3 py-2">구분</th>
                <th className="px-3 py-2">우편번호</th>
                <th className="px-3 py-2">메모</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {areas.map((a) => (
                <tr key={a.areaId} className="border-t border-border">
                  <td className="px-3 py-2">{a.areaType === "JEJU" ? "제주" : "도서산간"}</td>
                  <td className="px-3 py-2 tabular-nums">
                    {a.postcodeFrom}
                    {a.postcodeTo !== a.postcodeFrom ? ` ~ ${a.postcodeTo}` : ""}
                  </td>
                  <td className="px-3 py-2 text-muted-foreground">{a.note ?? "-"}</td>
                  <td className="px-3 py-2 text-right">
                    <button
                      type="button"
                      disabled={busy !== null}
                      className="text-xs text-danger hover:underline disabled:opacity-50"
                      onClick={() => {
                        if (!window.confirm("이 지역을 삭제할까요?")) return;
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
                  <td colSpan={4} className="px-3 py-6 text-center text-muted-foreground">
                    등록된 지역이 없습니다.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>

      <section className="rounded-xl border border-border bg-surface p-4 text-sm">
        <h2 className="mb-3 text-base font-semibold">택배사</h2>
        <p className="mb-2 text-xs text-muted-foreground">사용 중인 택배사만 송장 등록 화면의 택배사 목록에 표시됩니다.</p>
        <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {companies.map((c) => (
            <li key={c.code} className="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2">
              <span>
                {c.companyName} <span className="text-xs text-muted-foreground">({c.code})</span>
              </span>
              <label className="flex items-center gap-1.5 text-xs">
                <input
                  type="checkbox"
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
                사용
              </label>
            </li>
          ))}
        </ul>
      </section>

      <section className="rounded-xl border border-border bg-surface p-4 text-sm">
        <h2 className="mb-3 text-base font-semibold">배송조회 API 연동 상태</h2>
        {integration ? (
          <dl className="grid grid-cols-[160px_1fr] gap-y-1.5">
            <dt className="text-muted-foreground">설정된 연동사</dt>
            <dd>{integration.requestedProvider}</dd>
            <dt className="text-muted-foreground">실제 사용 중</dt>
            <dd>
              {integration.activeProvider}
              {integration.externalTracking ? (
                <span className="ml-2 text-xs text-brand">실시간 조회 사용</span>
              ) : (
                <span className="ml-2 text-xs text-accent">수동 모드 (API 키 미설정 또는 미지원)</span>
              )}
            </dd>
            <dt className="text-muted-foreground">API 키</dt>
            <dd>{integration.apiKeyConfigured ? "설정됨 (서버 환경변수)" : "미설정 — SHIPPING_API_KEY 환경변수 필요"}</dd>
            <dt className="text-muted-foreground">자동 조회 스케줄러</dt>
            <dd>
              {integration.schedulerEnabled ? `사용 (${integration.schedulerCron})` : "사용 안 함"} · 캐시{" "}
              {integration.cacheMinutes}분
            </dd>
            <dt className="text-muted-foreground">집하/회수 요청</dt>
            <dd>{integration.pickupService}</dd>
            <dt className="text-muted-foreground">운송장 발급/출력</dt>
            <dd>{integration.waybillSupported ? "사용 가능" : "미지원 (굿스플로 등 계약 API 연동 필요)"}</dd>
          </dl>
        ) : (
          <p className="text-muted-foreground">불러오는 중...</p>
        )}
      </section>
    </div>
  );
}
