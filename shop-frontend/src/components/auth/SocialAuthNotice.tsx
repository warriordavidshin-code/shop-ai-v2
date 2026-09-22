"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

function socialSignupMessage(provider: string | null): string | null {
  if (!provider) {
    return null;
  }
  if (provider === "kakao") {
    return "카카오 회원가입과 로그인이 완료되었습니다.";
  }
  if (provider === "naver") {
    return "네이버 회원가입과 로그인이 완료되었습니다.";
  }
  return "소셜 회원가입과 로그인이 완료되었습니다.";
}

export function SocialAuthNotice() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    const nextMessage = socialSignupMessage(searchParams.get("social_signup"));
    if (!nextMessage) {
      return;
    }
    setMessage(nextMessage);
    const params = new URLSearchParams(searchParams.toString());
    params.delete("social_signup");
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });

    const timer = window.setTimeout(() => setMessage(null), 4000);
    return () => window.clearTimeout(timer);
  }, [pathname, router, searchParams]);

  if (!message) {
    return null;
  }

  return (
    <div
      className="fixed bottom-4 left-1/2 z-50 w-[min(92vw,28rem)] -translate-x-1/2 rounded-2xl bg-brand px-4 py-3 text-center text-sm font-medium text-white shadow-lg"
      role="status"
    >
      {message}
    </div>
  );
}
