"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { logout } from "@/features/auth/api";
import type { Member } from "@/features/auth/schemas";

const genderLabel: Record<Member["gender"], string> = {
  FEMALE: "여성",
  MALE: "남성",
  OTHER: "기타",
  PREFER_NOT_TO_SAY: "선택 안 함",
};

export function MypageClient({ member }: { member: Member }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);

  async function onLogout() {
    setBusy(true);
    try {
      await logout();
      router.push("/");
      router.refresh();
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <dl className="grid gap-4 rounded-xl border border-border bg-surface p-6 text-sm">
        <Row label="이름" value={member.name} />
        <Row label="이메일" value={member.email} />
        <Row label="생년월일" value={member.birthDate} />
        <Row label="만 나이" value={`${member.age}세`} />
        <Row label="성별" value={genderLabel[member.gender]} />
        <Row label="연락처" value={member.phone} />
        <Row label="우편번호" value={member.postcode} />
        <Row label="기본주소" value={member.address1} />
        <Row label="상세주소" value={member.address2 ?? "-"} />
      </dl>
      <button
        type="button"
        onClick={onLogout}
        disabled={busy}
        className="inline-flex h-11 w-fit items-center justify-center rounded-xl border border-border bg-surface px-5 text-sm font-medium hover:bg-surface-soft disabled:opacity-60"
      >
        {busy ? "로그아웃 중..." : "로그아웃"}
      </button>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid grid-cols-[120px_1fr] gap-3">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="text-foreground">{value}</dd>
    </div>
  );
}
