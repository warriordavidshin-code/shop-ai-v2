import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { Container } from "@/components/ui/Container";

async function fetchAdminMembers() {
  const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";
  const cookieStore = await cookies();
  const cookieHeader = cookieStore
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join("; ");
  const response = await fetch(`${backendUrl}/api/admin/members?page=0&size=50`, {
    headers: cookieHeader ? { cookie: cookieHeader } : {},
    cache: "no-store",
  });
  if (response.status === 401 || response.status === 403) return null;
  if (!response.ok) return { content: [] };
  return response.json();
}

export default async function AdminMembersPage() {
  const data = await fetchAdminMembers();
  if (data === null) redirect("/login?next=/admin/members");
  const rows = Array.isArray(data.content) ? data.content : [];

  return (
    <main className="py-10">
      <Container className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">회원 관리</h1>
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-surface-soft text-muted-foreground">
              <tr>
                <th className="px-4 py-3">ID</th>
                <th className="px-4 py-3">이메일</th>
                <th className="px-4 py-3">이름</th>
                <th className="px-4 py-3">상태</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(
                (row: { memberId: number; email: string; name: string; status: string }) => (
                  <tr key={row.memberId} className="border-t border-border">
                    <td className="px-4 py-3">{row.memberId}</td>
                    <td className="px-4 py-3">{row.email}</td>
                    <td className="px-4 py-3">{row.name}</td>
                    <td className="px-4 py-3">{row.status}</td>
                  </tr>
                ),
              )}
            </tbody>
          </table>
        </div>
      </Container>
    </main>
  );
}
