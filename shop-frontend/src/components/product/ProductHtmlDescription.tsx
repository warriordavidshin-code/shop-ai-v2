"use client";

import { resolveMediaUrl } from "@/lib/media";

export function ProductHtmlDescription({ html }: { html: string }) {
  const looksLikeHtml = /<\/?[a-z][\s\S]*>/i.test(html);
  if (!looksLikeHtml) {
    return <p className="whitespace-pre-wrap">{html}</p>;
  }
  return (
    <div
      className="product-html prose prose-sm max-w-none text-muted-foreground [&_h2]:text-foreground [&_h3]:text-foreground [&_a]:text-brand [&_img]:max-w-full [&_img]:rounded-xl"
      dangerouslySetInnerHTML={{ __html: rewriteMediaUrls(html) }}
    />
  );
}

function rewriteMediaUrls(html: string): string {
  return html.replace(/(src=["'])(\/uploads\/[^"']+)(["'])/g, (_m, a, path, b) => {
    return `${a}${resolveMediaUrl(path)}${b}`;
  });
}
