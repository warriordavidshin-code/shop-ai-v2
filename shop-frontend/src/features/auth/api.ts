import { memberSchema, type Member, type LoginValues, type SignupValues } from "./schemas";

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

async function shopFetch(path: string, init: RequestInit = {}): Promise<Response> {
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

async function parseError(response: Response): Promise<ApiError> {
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

export async function login(values: LoginValues): Promise<Member> {
  const response = await shopFetch("/auth/login", {
    method: "POST",
    body: JSON.stringify(values),
  });
  if (!response.ok) {
    throw await parseError(response);
  }
  return memberSchema.parse(await response.json());
}

export async function signup(values: SignupValues): Promise<Member> {
  const payload = {
    loginId: values.loginId,
    email: values.email || null,
    password: values.password,
    name: values.name,
    birthDate: values.birthDate,
    gender: values.gender,
    phone: values.phone,
    postcode: values.postcode,
    address1: values.address1,
    address2: values.address2,
    termsAgreed: values.termsAgreed,
    privacyAgreed: values.privacyAgreed,
  };
  const response = await shopFetch("/auth/signup", {
    method: "POST",
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw await parseError(response);
  }
  return memberSchema.parse(await response.json());
}

export async function logout(): Promise<void> {
  try {
    const response = await shopFetch("/auth/logout", { method: "POST" });
    if (!response.ok && response.status !== 204) {
      throw await parseError(response);
    }
  } catch {
    // Cookie clear or navigation must still proceed on the client.
  }
}

export async function getMe(): Promise<Member | null> {
  const response = await shopFetch("/members/me");
  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw await parseError(response);
  }
  return memberSchema.parse(await response.json());
}
