import { redirect } from "next/navigation";
import { ProfileEditClient } from "@/components/member/ProfileEditClient";
import { memberSchema } from "@/features/auth/schemas";
import { reauthStatusSchema } from "@/features/members/api";
import { backendFetch } from "@/lib/api/server";

export default async function ProfileEditPage() {
  const [meResponse, reauthResponse] = await Promise.all([
    backendFetch("/members/me"),
    backendFetch("/members/me/reauth"),
  ]);
  if (!meResponse.ok) {
    redirect("/login?next=/mypage/verify");
  }
  const reauth = reauthResponse.ok ? reauthStatusSchema.safeParse(await reauthResponse.json()) : null;
  if (!reauth?.success || !reauth.data.verified) {
    redirect("/mypage/verify");
  }
  const member = memberSchema.parse(await meResponse.json());

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="heading-ko text-2xl text-foreground">개인정보 수정</h1>
        <p className="mt-1 text-sm text-muted-foreground">변경할 항목을 수정한 뒤 저장해 주세요.</p>
      </div>
      <ProfileEditClient member={member} expiresAt={reauth.data.expiresAt} />
    </div>
  );
}
