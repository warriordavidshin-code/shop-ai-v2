import { NextRequest, NextResponse } from "next/server";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";
const FALLBACK_ORIGIN = process.env.FRONTEND_URL ?? "https://btc-camel.com";

function mapOAuthBrowserError(message: string | undefined): string {
  const text = message ?? "";
  if (text.includes("설정되지 않았습니다")) {
    return "oauth_not_configured";
  }
  if (text.includes("필수 동의") || text.includes("이메일 동의")) {
    return "oauth_consent_required";
  }
  if (text.includes("이미 가입된 이메일")) {
    return "oauth_email_conflict";
  }
  return "oauth_failed";
}

function isOAuthBrowserPath(path: string): boolean {
  return /^(auth\/(kakao|naver)\/(login|callback))$/.test(path);
}

function isUsableHost(host: string | null | undefined): host is string {
  if (!host) {
    return false;
  }
  const value = host.split(",")[0]?.trim().toLowerCase() ?? "";
  if (!value) {
    return false;
  }
  // Next.js bind address must never become a browser redirect target.
  if (value.startsWith("0.0.0.0") || value.startsWith("[::]") || value.startsWith("::")) {
    return false;
  }
  return true;
}

/**
 * Prefer reverse-proxy forwarded host, then Host header, then FRONTEND_URL.
 * Avoids redirects like https://0.0.0.0:3000 when HOSTNAME=0.0.0.0.
 */
function publicOrigin(request: NextRequest): string {
  const forwardedHost = request.headers.get("x-forwarded-host");
  const forwardedProto = request.headers.get("x-forwarded-proto");
  if (isUsableHost(forwardedHost)) {
    const proto = forwardedProto?.split(",")[0]?.trim() || "https";
    return `${proto}://${forwardedHost.split(",")[0].trim()}`;
  }

  const host = request.headers.get("host");
  if (isUsableHost(host)) {
    const proto =
      forwardedProto?.split(",")[0]?.trim() ||
      (host.includes("localhost") || host.startsWith("127.0.0.1") ? "http" : "https");
    return `${proto}://${host}`;
  }

  return FALLBACK_ORIGIN.replace(/\/$/, "");
}

async function proxy(request: NextRequest, pathSegments: string[]) {
  const path = pathSegments.join("/");
  const url = new URL(request.url);
  const target = `${BACKEND_URL}/api/${path}${url.search}`;

  const headers = new Headers();
  const cookie = request.headers.get("cookie");
  if (cookie) {
    headers.set("cookie", cookie);
  }
  const contentType = request.headers.get("content-type");
  if (contentType) {
    headers.set("content-type", contentType);
  }
  const csrf = request.headers.get("x-xsrf-token");
  if (csrf) {
    headers.set("x-xsrf-token", csrf);
  }

  const init: RequestInit = {
    method: request.method,
    headers,
    redirect: "manual",
  };

  if (request.method !== "GET" && request.method !== "HEAD") {
    init.body = await request.arrayBuffer();
  }

  const backendResponse = await fetch(target, init);
  const responseHeaders = new Headers();
  const setCookie = backendResponse.headers.getSetCookie?.() ?? [];
  for (const value of setCookie) {
    responseHeaders.append("set-cookie", value);
  }
  const location = backendResponse.headers.get("location");
  if (location) {
    responseHeaders.set("location", location);
  }
  const responseContentType = backendResponse.headers.get("content-type");
  if (responseContentType) {
    responseHeaders.set("content-type", responseContentType);
  }

  const emptyBody =
    backendResponse.status === 204 ||
    backendResponse.status === 205 ||
    backendResponse.status === 304;

  // Browser OAuth navigations should never render raw backend JSON errors.
  if (
    request.method === "GET" &&
    isOAuthBrowserPath(path) &&
    backendResponse.status >= 400 &&
    (responseContentType ?? "").includes("application/json")
  ) {
    let message: string | undefined;
    try {
      const json = (await backendResponse.json()) as { message?: string };
      message = json.message;
    } catch {
      // ignore parse errors
    }
    const errorCode = mapOAuthBrowserError(message);
    const origin = publicOrigin(request);
    return NextResponse.redirect(`${origin}/login?error=${errorCode}`, 302);
  }

  const body = emptyBody ? null : await backendResponse.arrayBuffer();
  return new NextResponse(body, {
    status: backendResponse.status,
    headers: responseHeaders,
  });
}

type RouteContext = { params: Promise<{ path: string[] }> };

export async function GET(request: NextRequest, context: RouteContext) {
  const { path } = await context.params;
  return proxy(request, path);
}

export async function POST(request: NextRequest, context: RouteContext) {
  const { path } = await context.params;
  return proxy(request, path);
}

export async function PATCH(request: NextRequest, context: RouteContext) {
  const { path } = await context.params;
  return proxy(request, path);
}

export async function PUT(request: NextRequest, context: RouteContext) {
  const { path } = await context.params;
  return proxy(request, path);
}

export async function DELETE(request: NextRequest, context: RouteContext) {
  const { path } = await context.params;
  return proxy(request, path);
}
