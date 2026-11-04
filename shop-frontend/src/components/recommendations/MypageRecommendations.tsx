"use client";

import { useEffect, useState } from "react";
import { RecoSection } from "@/components/recommendations/RecoSection";
import { getPersonalization, setPersonalization } from "@/features/recommendations/api";

export function MypageRecommendations() {
  const [state, setState] = useState<{ enabled: boolean; description: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    void getPersonalization().then(setState);
  }, []);

  async function toggle(enabled: boolean) {
    if (!enabled && !window.confirm("맞춤 추천을 끄면 저장된 취향 정보와 최근 본·검색 기록이 삭제됩니다. 계속할까요?")) {
      return;
    }
    setBusy(true);
    setMessage(null);
    try {
      const ok = await setPersonalization(enabled);
      if (ok) {
        setState((s) => (s ? { ...s, enabled } : { enabled, description: "" }));
        setMessage(enabled ? "맞춤 추천을 켰습니다." : "맞춤 추천을 끄고 관련 기록을 삭제했습니다.");
        setVersion((v) => v + 1);
      } else {
        setMessage("설정을 변경하지 못했습니다.");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <section className="flex flex-col gap-2 rounded-xl border border-border bg-surface p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="font-medium text-foreground">맞춤 추천</p>
            <p className="mt-1 text-sm text-muted-foreground">
              {state?.description || "조회·검색·구매 이력을 바탕으로 취향에 맞는 상품을 추천합니다."}
            </p>
          </div>
          {state ? (
            <label className="inline-flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                className="h-5 w-5"
                checked={state.enabled}
                disabled={busy}
                onChange={(e) => void toggle(e.target.checked)}
              />
              {state.enabled ? "사용 중" : "사용 안 함"}
            </label>
          ) : null}
        </div>
        {message ? (
          <p className="text-sm text-foreground" role="status">
            {message}
          </p>
        ) : null}
      </section>
      <RecoSection key={`taste-${version}`} path="personalized?placement=MYPAGE_TASTE" columns={4} />
      <RecoSection key={`recent-${version}`} path="recent-similar" columns={4} />
    </div>
  );
}
