import type { ReactNode } from "react";
import { Container } from "@/components/ui/Container";
import { requireAdmin } from "@/features/admin/guard";

/** Server wrapper: redirects non-admins to login before rendering the client screen. */
export async function AdminPage({ next, children }: { next: string; children: ReactNode }) {
  await requireAdmin("/admin/ai/analysis-status", next);
  return (
    <main className="py-8">
      <Container className="flex flex-col gap-6">{children}</Container>
    </main>
  );
}
