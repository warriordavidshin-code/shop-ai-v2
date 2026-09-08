import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { Container } from "@/components/ui/Container";
import { AdminBannerManager } from "@/components/admin/AdminBannerManager";

async function fetchBanners() {
  const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";
  const cookieStore = await cookies();
  const cookieHeader = cookieStore
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join("; ");
  const response = await fetch(`${backendUrl}/api/admin/hero-banners`, {
    headers: cookieHeader ? { cookie: cookieHeader } : {},
    cache: "no-store",
  });
  if (response.status === 401 || response.status === 403) return null;
  if (!response.ok) return [];
  return response.json();
}

export default async function AdminBannersPage() {
  const data = await fetchBanners();
  if (data === null) redirect("/login?next=/admin/banners");

  return (
    <main className="py-10">
      <Container className="flex flex-col gap-6">
        <div>
          <h1 className="text-2xl font-semibold">메인 배너</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            홈 화면 우측 이미지 영역에 표시됩니다. 사용 중인 배너는 2초 간격으로 롤링됩니다.
          </p>
        </div>
        <AdminBannerManager initial={Array.isArray(data) ? data : []} />
      </Container>
    </main>
  );
}
