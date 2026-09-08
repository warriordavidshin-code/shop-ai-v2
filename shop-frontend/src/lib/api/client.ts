export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
    readonly fieldErrors?: Record<string, string>,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

function getCsrfToken(): string | undefined {
  if (typeof document === "undefined") {
    return undefined;
  }
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : undefined;
}

export async function shopFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  if (!headers.has("Content-Type") && init.body) {
    headers.set("Content-Type", "application/json");
  }
  const csrf = getCsrfToken();
  if (csrf && init.method && init.method !== "GET" && init.method !== "HEAD") {
    headers.set("X-XSRF-TOKEN", csrf);
  }

  return fetch(`/api/shop${path}`, {
    ...init,
    headers,
    credentials: "include",
  });
}

export async function parseApiError(response: Response): Promise<ApiError> {
  try {
    const data = (await response.json()) as {
      message?: string;
      code?: string;
      fieldErrors?: Record<string, string>;
    };
    return new ApiError(
      data.message ?? "요청을 처리할 수 없습니다.",
      response.status,
      data.code,
      data.fieldErrors,
    );
  } catch {
    return new ApiError("요청을 처리할 수 없습니다.", response.status);
  }
}
