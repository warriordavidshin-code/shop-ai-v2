"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { ApiError } from "@/lib/api/client";
import { socialReauthUrl, verifyPassword } from "@/features/members/api";

const ERROR_MESSAGES: Record<string, string> = {
  reauth_expired: "본인 인증 유효시간이 지났습니다. 다시 인증해 주세요.",
  reauth_denied: "소셜 계정 인증이 취소되었습니다. 다시 시도해 주세요.",
  reauth_failed: "본인 인증에 실패했습니다. 로그인한 계정과 같은 소셜 계정으로 인증해 주세요.",
  oauth_not_configured: "소셜 인증이 설정되지 않았습니다. 관리자에게 문의해 주세요.",
  oauth_failed: "본인 인증에 실패했습니다. 잠시 후 다시 시도해 주세요.",
};

type Props = {
  provider: "LOCAL" | "KAKAO" | "NAVER";
  loginId: string;
  errorCode?: string;
};

export function ReauthClient({ provider, loginId, errorCode }: Props) {
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(
    errorCode ? (ERROR_MESSAGES[errorCode] ?? ERROR_MESSAGES.oauth_failed) : null,
  );

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!password) {
      setError("비밀번호를 입력해 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await verifyPassword(password);
      router.push("/mypage/profile");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "본인 인증에 실패했습니다.");
      setBusy(false);
    }
  }

  if (provider !== "LOCAL") {
    const label = provider === "KAKAO" ? "카카오" : "네이버";
    return (
      <div className="flex flex-col gap-4">
        <p className="text-sm text-muted-foreground">
          {label} 계정으로 가입한 회원입니다. {label} 로그인 화면에서 다시 인증하면 개인정보 수정 화면으로 이동합니다.
        </p>
        {error ? (
          <p className="text-sm text-danger" role="alert">
            {error}
          </p>
        ) : null}
        <a
          href={socialReauthUrl(provider)}
          className={
            provider === "KAKAO"
              ? "inline-flex h-11 items-center justify-center rounded-xl bg-[#FEE500] px-5 text-sm font-medium text-[#191919] hover:brightness-95"
              : "inline-flex h-11 items-center justify-center rounded-xl bg-[#03C75A] px-5 text-sm font-medium text-white hover:brightness-95"
          }
        >
          {label} 계정으로 본인 인증
        </a>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
      <p className="text-sm text-muted-foreground">
        회원님의 정보를 안전하게 보호하기 위해 비밀번호를 다시 한번 확인합니다.
      </p>
      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">아이디</span>
        <input
          className="h-11 rounded-xl border border-border bg-surface-soft px-3 text-muted-foreground"
          value={loginId}
          readOnly
        />
      </label>
      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">비밀번호</span>
        <input
          type="password"
          autoComplete="current-password"
          autoFocus
          className="h-11 rounded-xl border border-border bg-surface px-3 outline-none focus-visible:ring-2 focus-visible:ring-brand"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          disabled={busy}
        />
      </label>
      {error ? (
        <p className="text-sm text-danger" role="alert">
          {error}
        </p>
      ) : null}
      <Button type="submit" disabled={busy}>
        {busy ? "확인 중..." : "확인"}
      </Button>
    </form>
  );
}
