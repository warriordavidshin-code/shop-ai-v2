import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { Container } from "@/components/ui/Container";

async function fetchAiStats() {
  const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";
  const cookieStore = await cookies();
  const cookieHeader = cookieStore
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join("; ");
  const response = await fetch(`${backendUrl}/api/admin/ai`, {
    headers: cookieHeader ? { cookie: cookieHeader } : {},
    cache: "no-store",
  });
  if (response.status === 401 || response.status === 403) return null;
  if (!response.ok) return {};
  return response.json();
}

export default async function AdminAiPage() {
  const data = await fetchAiStats();
  if (data === null) redirect("/login?next=/admin/ai");

  return (
    <main className="py-10">
      <Container className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">AI MD 통계</h1>
        <pre className="overflow-x-auto rounded-xl border border-border bg-surface-soft p-4 text-xs">
          {JSON.stringify(data, null, 2)}
        </pre>
      </Container>
    </main>
  );
}
