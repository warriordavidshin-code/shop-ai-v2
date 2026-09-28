"use client";

import Link from "next/link";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { ApiError } from "@/lib/api/client";
import { PASSWORD_POLICY_HINT, type Member } from "@/features/auth/schemas";
import { changePassword, clearReauth, updateProfile } from "@/features/members/api";
import { formatDateTime } from "@/features/orders/status";

const inputClass =
  "h-11 w-full rounded-xl border border-border bg-surface px-3 text-foreground outline-none focus-visible:ring-2 focus-visible:ring-brand";

const genders = [
  { value: "FEMALE", label: "여성" },
  { value: "MALE", label: "남성" },
  { value: "OTHER", label: "기타" },
  { value: "PREFER_NOT_TO_SAY", label: "선택 안 함" },
] as const;

type Gender = (typeof genders)[number]["value"];

export function ProfileEditClient({ member, expiresAt }: { member: Member; expiresAt?: string | null }) {
  const router = useRouter();
  const [form, setForm] = useState({
    name: member.name,
    phone: member.phone ?? "",
    birthDate: member.birthDate ?? "",
    gender: (member.gender ?? "PREFER_NOT_TO_SAY") as Gender,
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [pw, setPw] = useState({ current: "", next: "", confirm: "" });
  const [pwBusy, setPwBusy] = useState(false);
  const [pwMessage, setPwMessage] = useState<{ ok: boolean; text: string } | null>(null);

  function handleReauthExpired(e: unknown) {
    if (e instanceof ApiError && e.code === "REAUTH_REQUIRED") {
      router.push("/mypage/verify?error=reauth_expired");
      return true;
    }
    return false;
  }

  async function onSave(e: React.FormEvent) {
    e.preventDefault();
    if (form.name.trim().length < 2) {
      setError("이름은 2자 이상 입력해 주세요.");
      return;
    }
    if (form.phone && !/^[0-9+\-\s]{9,32}$/.test(form.phone.trim())) {
      setError("연락처를 올바르게 입력해 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await updateProfile({
        name: form.name.trim(),
        phone: form.phone.trim(),
        birthDate: form.birthDate || null,
        gender: form.gender,
      });
      await clearReauth();
      router.push("/mypage?updated=1");
      router.refresh();
    } catch (err) {
      if (handleReauthExpired(err)) return;
      setError(err instanceof ApiError ? err.message : "개인정보를 저장하지 못했습니다.");
      setBusy(false);
    }
  }

  async function onChangePassword(e: React.FormEvent) {
    e.preventDefault();
    if (pw.next !== pw.confirm) {
      setPwMessage({ ok: false, text: "새 비밀번호가 일치하지 않습니다." });
      return;
    }
    setPwBusy(true);
    setPwMessage(null);
    try {
      await changePassword(pw.current, pw.next);
      setPw({ current: "", next: "", confirm: "" });
      setPwMessage({ ok: true, text: "비밀번호가 변경되었습니다." });
    } catch (err) {
      setPwMessage({ ok: false, text: err instanceof ApiError ? err.message : "비밀번호를 변경하지 못했습니다." });
    } finally {
      setPwBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      {expiresAt ? (
        <p className="rounded-lg bg-surface-soft px-3 py-2 text-xs text-muted-foreground">
          본인 인증 유효시간: {formatDateTime(expiresAt)}까지. 시간이 지나면 다시 인증해야 합니다.
        </p>
      ) : null}

      <form onSubmit={onSave} className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6" noValidate>
        <h2 className="text-base font-semibold">기본 정보</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium">아이디</span>
            <input className={`${inputClass} bg-surface-soft text-muted-foreground`} value={member.loginId} readOnly />
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium">이메일</span>
            <input
              className={`${inputClass} bg-surface-soft text-muted-foreground`}
              value={member.email ?? "-"}
              readOnly
            />
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium">이름</span>
            <input
              className={inputClass}
              value={form.name}
              maxLength={100}
              onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
              disabled={busy}
            />
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium">연락처</span>
            <input
              className={inputClass}
              value={form.phone}
              inputMode="tel"
              maxLength={32}
              onChange={(e) => setForm((p) => ({ ...p, phone: e.target.value }))}
              disabled={busy}
            />
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium">생년월일</span>
            <input
              type="date"
              className={inputClass}
              value={form.birthDate}
              onChange={(e) => setForm((p) => ({ ...p, birthDate: e.target.value }))}
              disabled={busy}
            />
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium">성별</span>
            <select
              className={inputClass}
              value={form.gender}
              onChange={(e) => setForm((p) => ({ ...p, gender: e.target.value as Gender }))}
              disabled={busy}
            >
              {genders.map((g) => (
                <option key={g.value} value={g.value}>
                  {g.label}
                </option>
              ))}
            </select>
          </label>
        </div>
        <p className="text-xs text-muted-foreground">
          배송지는{" "}
          <Link href="/mypage/addresses" className="text-brand hover:underline">
            배송지 관리
          </Link>
          에서 변경할 수 있습니다.
        </p>
        {error ? (
          <p className="text-sm text-danger" role="alert">
            {error}
          </p>
        ) : null}
        <div className="flex gap-2">
          <Button type="submit" disabled={busy}>
            {busy ? "저장 중..." : "저장"}
          </Button>
          <Button
            variant="secondary"
            disabled={busy}
            onClick={async () => {
              await clearReauth();
              router.push("/mypage");
            }}
          >
            취소
          </Button>
        </div>
      </form>

      {member.authProvider === "LOCAL" || !member.authProvider ? (
        <form
          onSubmit={onChangePassword}
          className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6"
          noValidate
        >
          <h2 className="text-base font-semibold">비밀번호 변경</h2>
          <div className="grid gap-4 sm:grid-cols-3">
            {(
              [
                ["current", "현재 비밀번호", "current-password"],
                ["next", "새 비밀번호", "new-password"],
                ["confirm", "새 비밀번호 확인", "new-password"],
              ] as const
            ).map(([key, label, autoComplete]) => (
              <label key={key} className="flex flex-col gap-1.5 text-sm">
                <span className="font-medium">{label}</span>
                <input
                  type="password"
                  autoComplete={autoComplete}
                  className={inputClass}
                  value={pw[key]}
                  onChange={(e) => setPw((p) => ({ ...p, [key]: e.target.value }))}
                  disabled={pwBusy}
                />
              </label>
            ))}
          </div>
          <p className="text-xs text-muted-foreground">{PASSWORD_POLICY_HINT}</p>
          {pwMessage ? (
            <p className={pwMessage.ok ? "text-sm text-success" : "text-sm text-danger"} role="status">
              {pwMessage.text}
            </p>
          ) : null}
          <Button type="submit" variant="secondary" disabled={pwBusy || !pw.current || !pw.next} className="w-fit">
            {pwBusy ? "변경 중..." : "비밀번호 변경"}
          </Button>
        </form>
      ) : null}
    </div>
  );
}
