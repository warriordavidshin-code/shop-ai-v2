"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Eye, EyeOff } from "lucide-react";
import { SocialLoginButtons } from "@/components/auth/SocialLoginButtons";
import { ApiError, login, resetPassword } from "@/features/auth/api";
import {
  loginSchema,
  passwordResetSchema,
  type LoginValues,
  type PasswordResetValues,
} from "@/features/auth/schemas";

function safeRedirect(path: string | null): string {
  if (!path || !path.startsWith("/") || path.startsWith("//")) {
    return "/";
  }
  return path;
}

function oauthErrorMessage(code: string | null): string | null {
  if (!code) {
    return null;
  }
  switch (code) {
    case "oauth_denied":
      return "소셜 로그인이 취소되었습니다.";
    case "oauth_missing_code":
      return "소셜 로그인 인증 코드가 없습니다. 다시 시도해 주세요.";
    case "oauth_email_conflict":
      return "이미 가입된 이메일입니다. 기존 계정으로 로그인해 주세요.";
    case "oauth_consent_required":
      return "카카오 필수 동의 항목을 모두 허용한 뒤 다시 시도해 주세요.";
    case "oauth_not_configured":
      return "카카오 로그인이 아직 설정되지 않았습니다. 잠시 후 다시 시도해 주세요.";
    case "oauth_failed":
      return "소셜 로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.";
    default:
      return "소셜 로그인에 실패했습니다.";
  }
}

export function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [showPassword, setShowPassword] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [showReset, setShowReset] = useState(false);
  const [resetError, setResetError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const nextPath = safeRedirect(searchParams.get("next"));
  const oauthError = useMemo(
    () => oauthErrorMessage(searchParams.get("error")),
    [searchParams],
  );

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { loginId: "", password: "" },
  });

  const resetForm = useForm<PasswordResetValues>({
    resolver: zodResolver(passwordResetSchema),
    defaultValues: { loginId: "", name: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await login(values);
      router.push(nextPath);
      router.refresh();
    } catch (error) {
      if (error instanceof ApiError) {
        setFormError(error.message);
      } else {
        setFormError("로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.");
      }
    }
  });

  const onResetSubmit = resetForm.handleSubmit(async (values) => {
    setResetError(null);
    try {
      const result = await resetPassword(values);
      setShowReset(false);
      setSuccessMessage(result.message);
      resetForm.reset();
    } catch (error) {
      if (error instanceof ApiError) {
        setResetError(error.message);
      } else {
        setResetError("임시 비밀번호 발송에 실패했습니다. 잠시 후 다시 시도해 주세요.");
      }
    }
  });

  return (
    <div className="flex w-full flex-col gap-6">
      <form onSubmit={onSubmit} className="flex w-full flex-col gap-4" noValidate>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="loginId" className="text-sm font-medium text-foreground">
            아이디
          </label>
          <input
            id="loginId"
            type="text"
            autoComplete="username"
            className="h-11 rounded-xl border border-border bg-surface px-3 text-foreground outline-none focus-visible:ring-2 focus-visible:ring-brand"
            aria-invalid={!!errors.loginId}
            aria-describedby={errors.loginId ? "loginId-error" : undefined}
            {...register("loginId")}
          />
          {errors.loginId ? (
            <p id="loginId-error" className="text-sm text-danger">
              {errors.loginId.message}
            </p>
          ) : null}
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="password" className="text-sm font-medium text-foreground">
            비밀번호
          </label>
          <div className="relative">
            <input
              id="password"
              type={showPassword ? "text" : "password"}
              autoComplete="current-password"
              className="h-11 w-full rounded-xl border border-border bg-surface px-3 pr-12 text-foreground outline-none focus-visible:ring-2 focus-visible:ring-brand"
              aria-invalid={!!errors.password}
              aria-describedby={errors.password ? "password-error" : undefined}
              {...register("password")}
            />
            <button
              type="button"
              className="absolute right-2 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-lg text-muted-foreground hover:bg-surface-soft"
              aria-label={showPassword ? "비밀번호 숨기기" : "비밀번호 표시"}
              onClick={() => setShowPassword((v) => !v)}
            >
              {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </div>
          {errors.password ? (
            <p id="password-error" className="text-sm text-danger">
              {errors.password.message}
            </p>
          ) : null}
        </div>

        {formError || oauthError ? (
          <p className="rounded-xl bg-brand-soft/60 px-3 py-2 text-sm text-foreground" role="alert">
            {formError ?? oauthError}
          </p>
        ) : null}

        <button
          type="submit"
          disabled={isSubmitting}
          className="inline-flex h-11 items-center justify-center rounded-xl bg-brand px-5 text-sm font-medium text-white transition-colors duration-200 hover:bg-brand-hover disabled:opacity-60"
        >
          {isSubmitting ? "로그인 중..." : "로그인"}
        </button>

        <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-muted-foreground">
          <Link href="/signup" className="text-brand hover:underline">
            회원가입
          </Link>
          <button
            type="button"
            className="text-brand hover:underline"
            aria-label="비밀번호 찾기"
            onClick={() => {
              setResetError(null);
              setSuccessMessage(null);
              setShowReset(true);
            }}
          >
            비밀번호 찾기
          </button>
        </div>
      </form>

      <SocialLoginButtons redirect={nextPath} />

      {showReset ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="password-reset-title"
        >
          <div className="w-full max-w-md rounded-2xl bg-surface p-6 shadow-lg">
            <h2 id="password-reset-title" className="mb-2 text-lg font-semibold text-foreground">
              비밀번호 찾기
            </h2>
            <p className="mb-4 text-sm text-muted-foreground">
              가입 시 등록한 아이디와 이름을 입력하면 등록된 이메일로 임시 비밀번호를 발송합니다.
            </p>
            <form onSubmit={onResetSubmit} className="flex flex-col gap-3" noValidate>
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-foreground" htmlFor="reset-loginId">
                  아이디
                </label>
                <input
                  id="reset-loginId"
                  className="h-11 rounded-xl border border-border bg-background px-3 outline-none focus-visible:ring-2 focus-visible:ring-brand"
                  {...resetForm.register("loginId")}
                />
                {resetForm.formState.errors.loginId ? (
                  <p className="text-sm text-danger">{resetForm.formState.errors.loginId.message}</p>
                ) : null}
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-foreground" htmlFor="reset-name">
                  이름
                </label>
                <input
                  id="reset-name"
                  className="h-11 rounded-xl border border-border bg-background px-3 outline-none focus-visible:ring-2 focus-visible:ring-brand"
                  {...resetForm.register("name")}
                />
                {resetForm.formState.errors.name ? (
                  <p className="text-sm text-danger">{resetForm.formState.errors.name.message}</p>
                ) : null}
              </div>
              {resetError ? (
                <p className="rounded-xl bg-brand-soft/60 px-3 py-2 text-sm" role="alert">
                  {resetError}
                </p>
              ) : null}
              <div className="mt-2 flex gap-2">
                <button
                  type="button"
                  className="inline-flex h-11 flex-1 items-center justify-center rounded-xl border border-border px-4 text-sm"
                  onClick={() => setShowReset(false)}
                >
                  취소
                </button>
                <button
                  type="submit"
                  disabled={resetForm.formState.isSubmitting}
                  className="inline-flex h-11 flex-1 items-center justify-center rounded-xl bg-brand px-4 text-sm font-medium text-white disabled:opacity-60"
                >
                  {resetForm.formState.isSubmitting ? "발송 중..." : "임시 비밀번호 발송"}
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}

      {successMessage ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="password-reset-success-title"
        >
          <div className="w-full max-w-md rounded-2xl bg-surface p-6 shadow-lg">
            <h2 id="password-reset-success-title" className="mb-3 text-lg font-semibold text-foreground">
              안내
            </h2>
            <p className="mb-6 text-sm leading-relaxed text-foreground">{successMessage}</p>
            <button
              type="button"
              className="inline-flex h-11 w-full items-center justify-center rounded-xl bg-brand px-4 text-sm font-medium text-white"
              onClick={() => setSuccessMessage(null)}
            >
              확인
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
