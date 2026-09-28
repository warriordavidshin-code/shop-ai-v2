import { redirect } from "next/navigation";
import { ReauthClient } from "@/components/member/ReauthClient";
import { memberSchema } from "@/features/auth/schemas";
import { backendFetch } from "@/lib/api/server";

type SearchParams = Promise<{ error?: string }>;

export default async function VerifyPage({ searchParams }: { searchParams: SearchParams }) {
  const { error } = await searchParams;
  const response = await backendFetch("/members/me");
  if (!response.ok) {
    redirect("/login?next=/mypage/verify");
  }
  const member = memberSchema.parse(await response.json());

  return (
    <div className="flex max-w-md flex-col gap-6">
      <div>
        <h1 className="heading-ko text-2xl text-foreground">본인 인증</h1>
        <p className="mt-1 text-sm text-muted-foreground">개인정보 수정 전 본인 확인이 필요합니다.</p>
      </div>
      <section className="rounded-xl border border-border bg-surface p-6">
        <ReauthClient provider={member.authProvider ?? "LOCAL"} loginId={member.loginId} errorCode={error} />
      </section>
    </div>
  );
}
