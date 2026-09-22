"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getMe, logout } from "@/features/auth/api";

type AuthState = "loading" | "guest" | "member";

export function HeaderAuthNav() {
  const router = useRouter();
  const [authState, setAuthState] = useState<AuthState>("loading");
  const [logoutBusy, setLogoutBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const me = await getMe();
        if (!cancelled) {
          setAuthState(me ? "member" : "guest");
        }
      } catch {
        if (!cancelled) {
          setAuthState("guest");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  async function onLogout() {
    setLogoutBusy(true);
    try {
      await logout();
    } finally {
      setAuthState("guest");
      router.push("/");
      router.refresh();
      setLogoutBusy(false);
    }
  }

  if (authState === "loading") {
    return <span className="inline-block h-4 w-20 animate-pulse rounded bg-surface-soft" aria-hidden />;
  }

  if (authState === "guest") {
    return (
      <Link href="/login" className="text-foreground hover:underline">
        로그인
      </Link>
    );
  }

  return (
    <>
      <Link href="/mypage" className="text-foreground hover:underline">
        마이페이지
      </Link>
      <button
        type="button"
        onClick={onLogout}
        disabled={logoutBusy}
        className="text-foreground hover:underline disabled:opacity-60"
      >
        {logoutBusy ? "로그아웃 중..." : "로그아웃"}
      </button>
    </>
  );
}
