"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { DaumPostcodeFields } from "@/components/address/DaumPostcodeFields";
import { SocialLoginButtons } from "@/components/auth/SocialLoginButtons";
import { PRIVACY_POLICY, TERMS_OF_SERVICE } from "@/content/legal";
import { ApiError, signup } from "@/features/auth/api";
import { signupSchema, type SignupValues } from "@/features/auth/schemas";

function calcDisplayAge(birthDate: string): number | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(birthDate)) {
    return null;
  }
  const today = new Date();
  const [y, m, d] = birthDate.split("-").map(Number);
  let age = today.getFullYear() - y;
  const beforeBirthday =
    today.getMonth() + 1 < m || (today.getMonth() + 1 === m && today.getDate() < d);
  if (beforeBirthday) {
    age -= 1;
  }
  return age >= 0 ? age : null;
}

export function SignupForm() {
  const router = useRouter();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<SignupValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: {
      loginId: "",
      email: "",
      password: "",
      passwordConfirm: "",
      name: "",
      birthDate: "",
      gender: "FEMALE",
      phone: "",
      postcode: "",
      address1: "",
      address2: "",
      termsAgreed: false,
      privacyAgreed: false,
    },
  });

  const birthDate = watch("birthDate");
  const displayAge = useMemo(() => calcDisplayAge(birthDate), [birthDate]);

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await signup(values);
      router.push("/mypage");
      router.refresh();
    } catch (error) {
      if (error instanceof ApiError) {
        setFormError(error.message);
      } else {
        setFormError("회원가입에 실패했습니다. 잠시 후 다시 시도해 주세요.");
      }
    }
  });

  return (
    <div className="flex flex-col gap-8">
    <form onSubmit={onSubmit} className="flex w-full flex-col gap-8" noValidate>
      <section className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold text-foreground">계정 정보</h2>
        <Field label="아이디" error={errors.loginId?.message} hint="영문으로 시작해 4~20자, 영문/숫자/밑줄">
          <input
            autoComplete="username"
            className={inputClass}
            {...register("loginId")}
          />
        </Field>
        <Field label="이메일 (선택)" error={errors.email?.message}>
          <input
            type="email"
            autoComplete="email"
            className={inputClass}
            {...register("email")}
          />
        </Field>
        <Field label="비밀번호" error={errors.password?.message}>
          <input type="password" autoComplete="new-password" className={inputClass} {...register("password")} />
        </Field>
        <Field label="비밀번호 확인" error={errors.passwordConfirm?.message}>
          <input type="password" autoComplete="new-password" className={inputClass} {...register("passwordConfirm")} />
        </Field>
      </section>

      <section className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold text-foreground">고객 정보</h2>
        <Field label="이름" error={errors.name?.message}>
          <input className={inputClass} {...register("name")} />
        </Field>
        <Field
          label="생년월일"
          error={errors.birthDate?.message}
          hint={displayAge !== null ? `만 ${displayAge}세 (화면 표시용, 서버에서 재계산)` : undefined}
        >
          <input type="date" className={inputClass} {...register("birthDate")} />
        </Field>
        <Field label="성별" error={errors.gender?.message}>
          <select className={inputClass} {...register("gender")}>
            <option value="FEMALE">여성</option>
            <option value="MALE">남성</option>
            <option value="OTHER">기타</option>
            <option value="PREFER_NOT_TO_SAY">선택 안 함</option>
          </select>
        </Field>
        <Field label="연락처" error={errors.phone?.message}>
          <input className={inputClass} {...register("phone")} placeholder="01012345678" />
        </Field>
      </section>

      <section className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold text-foreground">주소와 약관 동의</h2>
        <DaumPostcodeFields
          postcode={watch("postcode")}
          address1={watch("address1")}
          address2={watch("address2") ?? ""}
          onPostcodeChange={(value) => setValue("postcode", value, { shouldValidate: true, shouldDirty: true })}
          onAddress1Change={(value) => setValue("address1", value, { shouldValidate: true, shouldDirty: true })}
          onAddress2Change={(value) => setValue("address2", value, { shouldValidate: true, shouldDirty: true })}
          errors={{
            postcode: errors.postcode?.message,
            address1: errors.address1?.message,
            address2: errors.address2?.message,
          }}
          disabled={isSubmitting}
        />
        <div className="flex flex-col gap-2">
          <span className="text-sm font-medium text-foreground">이용약관</span>
          <textarea
            readOnly
            value={TERMS_OF_SERVICE}
            rows={8}
            className="w-full resize-y rounded-xl border border-border bg-surface-soft px-3 py-2 text-xs leading-5 text-foreground"
          />
          <label className="flex items-start gap-2 text-sm text-foreground">
            <input type="checkbox" className="mt-1 h-4 w-4" {...register("termsAgreed")} />
            <span>이용약관에 동의합니다.</span>
          </label>
          {errors.termsAgreed ? (
            <p className="text-sm text-danger">{errors.termsAgreed.message}</p>
          ) : null}
        </div>
        <div className="flex flex-col gap-2">
          <span className="text-sm font-medium text-foreground">개인정보 처리방침</span>
          <textarea
            readOnly
            value={PRIVACY_POLICY}
            rows={8}
            className="w-full resize-y rounded-xl border border-border bg-surface-soft px-3 py-2 text-xs leading-5 text-foreground"
          />
          <label className="flex items-start gap-2 text-sm text-foreground">
            <input type="checkbox" className="mt-1 h-4 w-4" {...register("privacyAgreed")} />
            <span>개인정보 수집·이용에 동의합니다.</span>
          </label>
          {errors.privacyAgreed ? (
            <p className="text-sm text-danger">{errors.privacyAgreed.message}</p>
          ) : null}
        </div>
      </section>

      {formError ? (
        <p className="rounded-xl bg-brand-soft/60 px-3 py-2 text-sm" role="alert">
          {formError}
        </p>
      ) : null}

      <button
        type="submit"
        disabled={isSubmitting}
        className="inline-flex h-11 items-center justify-center rounded-xl bg-brand px-5 text-sm font-medium text-white hover:bg-brand-hover disabled:opacity-60"
      >
        {isSubmitting ? "가입 중..." : "회원가입"}
      </button>
      <p className="text-sm text-muted-foreground">
        이미 계정이 있으신가요?{" "}
        <Link href="/login" className="text-brand hover:underline">
          로그인
        </Link>
      </p>
    </form>
    <SocialLoginButtons redirect="/mypage" />
    </div>
  );
}

const inputClass =
  "h-11 w-full rounded-xl border border-border bg-surface px-3 text-foreground outline-none focus-visible:ring-2 focus-visible:ring-brand";

function Field({
  label,
  error,
  hint,
  children,
}: {
  label: string;
  error?: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-sm font-medium text-foreground">{label}</span>
      {children}
      {hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
      {error ? <p className="text-sm text-danger">{error}</p> : null}
    </div>
  );
}
