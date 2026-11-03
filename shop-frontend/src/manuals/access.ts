import { operatorManual } from "./operatorManual";
import type { Manual } from "./render";
import { userManual } from "./userManual";

export const manuals: Record<Manual["slug"], Manual> = {
  operator: operatorManual,
  user: userManual,
};

export function findManual(slug: string): Manual | null {
  return slug === "operator" || slug === "user" ? manuals[slug] : null;
}

export function requiresAdmin(manual: Manual): boolean {
  return manual.slug === "operator";
}

export type ViewerRole = "ADMIN" | "CUSTOMER" | "ANONYMOUS";

export async function resolveViewerRole(cookieHeader: string | null, backendUrl: string): Promise<ViewerRole> {
  if (!cookieHeader) {
    return "ANONYMOUS";
  }
  try {
    const response = await fetch(`${backendUrl}/api/members/me`, {
      headers: { cookie: cookieHeader },
      cache: "no-store",
    });
    if (!response.ok) {
      return "ANONYMOUS";
    }
    const body = (await response.json()) as { role?: string };
    return body.role === "ADMIN" ? "ADMIN" : "CUSTOMER";
  } catch {
    return "ANONYMOUS";
  }
}

export function contentDisposition(fileName: string, download: boolean): string {
  const type = download ? "attachment" : "inline";
  return `${type}; filename="${fileName}"; filename*=UTF-8''${encodeURIComponent(fileName)}`;
}
