import { z } from "zod";
import { shopFetch } from "@/lib/api/client";
import { productSummarySchema } from "@/features/products/api";

export const recoListSchema = z.object({
  requestId: z.string().nullable(),
  placement: z.string(),
  title: z.string(),
  algorithm: z.string(),
  items: z.array(z.object({ product: productSummarySchema, reason: z.string().nullable().optional() })),
});

export type RecoList = z.infer<typeof recoListSchema>;

/** Recommendation failures must never break the page: any error yields null and the section stays hidden. */
export async function fetchRecommendations(path: string): Promise<RecoList | null> {
  try {
    const response = await shopFetch(`/recommendations/${path}`, { cache: "no-store" });
    if (!response.ok) return null;
    const parsed = recoListSchema.safeParse(await response.json());
    return parsed.success ? parsed.data : null;
  } catch {
    return null;
  }
}

function post(path: string, body: unknown) {
  return shopFetch(path, { method: "POST", body: JSON.stringify(body), keepalive: true }).catch(() => undefined);
}

export type RecoEventType = "IMPRESSION" | "CLICK" | "CART";

export function sendRecoEvent(requestId: string | null, eventType: RecoEventType, productId?: number, itemCount?: number) {
  if (!requestId) return;
  void post("/recommendations/events", { requestId, eventType, productId: productId ?? null, itemCount: itemCount ?? null });
}

export function trackProductView(productId: number) {
  void post("/recommendations/track/view", { productId });
}

export function trackSearch(keyword: string) {
  const text = keyword.trim();
  if (text) void post("/recommendations/track/search", { keyword: text.slice(0, 100) });
}

const REC_KEY = (productId: number) => `reco:rec:${productId}`;

/** Remembers which recommendation led to this product page so a later 장바구니 담기 can be attributed. */
export function rememberRecoSource(productId: number, requestId: string) {
  try {
    sessionStorage.setItem(REC_KEY(productId), requestId);
  } catch {
    // storage unavailable (private mode) — attribution is best effort
  }
}

export function recoSourceFor(productId: number): string | null {
  try {
    return sessionStorage.getItem(REC_KEY(productId));
  } catch {
    return null;
  }
}

export async function getPersonalization(): Promise<{ enabled: boolean; description: string } | null> {
  try {
    const response = await shopFetch("/members/me/personalization", { cache: "no-store" });
    if (!response.ok) return null;
    return (await response.json()) as { enabled: boolean; description: string };
  } catch {
    return null;
  }
}

export async function setPersonalization(enabled: boolean): Promise<boolean> {
  const response = await shopFetch("/members/me/personalization", {
    method: "PUT",
    body: JSON.stringify({ enabled }),
  });
  return response.ok;
}
