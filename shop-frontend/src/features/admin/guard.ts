import { redirect } from "next/navigation";
import { backendFetch } from "@/lib/api/server";

/** Server-side admin gate: probes an admin endpoint and sends non-admins to login. */
export async function requireAdmin(probePath: string, next: string) {
  const probe = await backendFetch(probePath);
  if (probe.status === 401 || probe.status === 403) {
    redirect(`/login?next=${encodeURIComponent(next)}`);
  }
  return probe;
}
