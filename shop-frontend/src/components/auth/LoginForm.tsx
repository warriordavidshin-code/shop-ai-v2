"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Eye, EyeOff } from "lucide-react";
import { ApiError, login } from "@/features/auth/api";
import { loginSchema, type LoginValues } from "@/features/auth/schemas";

function safeRedirect(path: string | null): string {
  if (!path || !path.startsWith("/") || path.startsWith("//")) {
    return "/";
  }
  return path;
}

export function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [showPassword, setShowPassword] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { loginId: "", password: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await login(values);
      router.push(safeRedirect(searchParams.get("next")));
      router.refresh();
    } catch (error) {
      if (error instanceof ApiError) {
        setFormError(error.message);
      } else {
        setFormError("로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.");
      }
    }
  });

  return (
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
          비밀번호 (특수문자 포함)
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

      {formError ? (
        <p className="rounded-xl bg-brand-soft/60 px-3 py-2 text-sm text-foreground" role="alert">
          {formError}
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
        <span className="cursor-not-allowed opacity-70" title="준비 중">
          비밀번호 찾기 (준비 중)
        </span>
      </div>
    </form>
  );
}
