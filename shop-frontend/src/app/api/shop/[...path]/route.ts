import { NextRequest, NextResponse } from "next/server";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

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
  const responseContentType = backendResponse.headers.get("content-type");
  if (responseContentType) {
    responseHeaders.set("content-type", responseContentType);
  }

  const emptyBody =
    backendResponse.status === 204 ||
    backendResponse.status === 205 ||
    backendResponse.status === 304;
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
