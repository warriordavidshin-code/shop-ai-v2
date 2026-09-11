"use client";

type SocialLoginButtonsProps = {
  redirect?: string;
};

function buildSocialUrl(provider: "kakao" | "naver", redirect?: string): string {
  const params = new URLSearchParams();
  if (redirect && redirect.startsWith("/") && !redirect.startsWith("//")) {
    params.set("redirect", redirect);
  }
  const query = params.toString();
  return `/api/shop/auth/${provider}/login${query ? `?${query}` : ""}`;
}

export function SocialLoginButtons({ redirect }: SocialLoginButtonsProps) {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-3 text-xs text-muted-foreground">
        <span className="h-px flex-1 bg-border" />
        간편 로그인
        <span className="h-px flex-1 bg-border" />
      </div>
      <a
        href={buildSocialUrl("kakao", redirect)}
        className="inline-flex h-11 items-center justify-center rounded-xl bg-[#FEE500] px-5 text-sm font-medium text-[#191919] transition-opacity hover:opacity-90"
      >
        카카오로 시작하기
      </a>
      <a
        href={buildSocialUrl("naver", redirect)}
        className="inline-flex h-11 items-center justify-center rounded-xl bg-[#03C75A] px-5 text-sm font-medium text-white transition-opacity hover:opacity-90"
      >
        네이버로 시작하기
      </a>
    </div>
  );
}
