import { NextRequest, NextResponse } from "next/server";
import { contentDisposition, findManual, requiresAdmin, resolveViewerRole } from "@/manuals/access";
import { renderManual } from "@/manuals/render";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

type RouteContext = { params: Promise<{ name: string }> };

function errorPage(status: number, message: string): NextResponse {
  const html = `<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>매뉴얼</title></head>
<body style="font-family:sans-serif;padding:40px;line-height:1.7"><p>${message}</p><p><a href="/login">로그인</a> · <a href="/">쇼핑몰로</a></p></body></html>`;
  return new NextResponse(html, {
    status,
    headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" },
  });
}

export async function GET(request: NextRequest, context: RouteContext) {
  const { name } = await context.params;
  const manual = findManual(name);
  if (!manual) {
    return errorPage(404, "매뉴얼을 찾을 수 없습니다.");
  }

  if (requiresAdmin(manual)) {
    const role = await resolveViewerRole(request.headers.get("cookie"), BACKEND_URL);
    if (role === "ANONYMOUS") {
      return errorPage(401, "운영자 매뉴얼은 관리자 로그인 후 볼 수 있습니다.");
    }
    if (role !== "ADMIN") {
      return errorPage(403, "운영자 매뉴얼은 관리자만 볼 수 있습니다.");
    }
  }

  const download = request.nextUrl.searchParams.get("download") === "1";
  const html = renderManual(manual, { downloadHref: `/manuals/${manual.slug}?download=1` });
  return new NextResponse(html, {
    status: 200,
    headers: {
      "content-type": "text/html; charset=utf-8",
      "content-disposition": contentDisposition(manual.downloadFileName, download),
      "cache-control": requiresAdmin(manual) ? "private, no-store" : "public, max-age=300",
      "x-content-type-options": "nosniff",
    },
  });
}
