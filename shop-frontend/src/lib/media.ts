export function resolveMediaUrl(url?: string | null): string {
  if (!url) return "";
  if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:")) {
    return url;
  }
  // Prefer same-origin path so Next.js rewrite can proxy /uploads/*
  if (url.startsWith("/")) return url;
  return `/${url}`;
}
