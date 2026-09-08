import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { MypageClient } from "@/components/member/MypageClient";
import { memberSchema, type Member } from "@/features/auth/schemas";

async function fetchMe(): Promise<Member | null> {
  const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";
  const cookieStore = await cookies();
  const cookieHeader = cookieStore
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join("; ");

  const response = await fetch(`${backendUrl}/api/members/me`, {
    headers: cookieHeader ? { cookie: cookieHeader } : {},
    cache: "no-store",
  });

  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    return null;
  }
  return memberSchema.parse(await response.json());
}

export default async function MypagePage() {
  const member = await fetchMe();
  if (!member) {
    redirect("/login?next=/mypage");
  }

  return (
    <main className="min-h-screen bg-background">
      <div className="mx-auto max-w-[720px] px-4 py-12 md:px-8">
        <p className="mb-2 text-sm tracking-wide text-muted-foreground">BoutiqueCamel</p>
        <h1 className="mb-8 text-3xl font-semibold text-foreground">마이페이지</h1>
        <MypageClient member={member} />
      </div>
    </main>
  );
}
