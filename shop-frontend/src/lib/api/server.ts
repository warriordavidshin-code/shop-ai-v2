import { cookies } from "next/headers";

/** Server-component fetch against the backend, forwarding the browser's auth cookies. */
export async function backendFetch(path: string): Promise<Response> {
  const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";
  const cookieStore = await cookies();
  const cookieHeader = cookieStore
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join("; ");
  return fetch(`${backendUrl}/api${path}`, {
    headers: cookieHeader ? { cookie: cookieHeader } : {},
    cache: "no-store",
  });
}
