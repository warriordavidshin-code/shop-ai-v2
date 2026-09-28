"use client";

import Link from "next/link";
import { useState } from "react";
import { logout } from "@/features/auth/api";
import type { Member } from "@/features/auth/schemas";

const providerLabel: Record<"LOCAL" | "KAKAO" | "NAVER", string> = {
  LOCAL: "이메일/아이디",
  KAKAO: "카카오",
  NAVER: "네이버",
};

export function MypageClient({ member }: { member: Member }) {
  const [busy, setBusy] = useState(false);

  async function onLogout() {
    setBusy(true);
    try {
      await logout();
    } finally {
      window.location.assign("/");
    }
  }

  return (
    <section className="flex flex-col gap-5 rounded-xl border border-border bg-surface p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-lg font-semibold text-foreground">{member.name}님</p>
          <p className="mt-1 text-sm text-muted-foreground">
            {member.loginId} · {providerLabel[member.authProvider ?? "LOCAL"]} 로그인
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link
            href="/mypage/verify"
            className="inline-flex h-10 items-center justify-center rounded-xl bg-brand px-4 text-sm font-medium text-white hover:bg-brand-hover"
          >
            개인정보 수정
          </Link>
          <button
            type="button"
            onClick={onLogout}
            disabled={busy}
            className="inline-flex h-10 items-center justify-center rounded-xl border border-border bg-surface px-4 text-sm font-medium hover:bg-surface-soft disabled:opacity-60"
          >
            {busy ? "로그아웃 중..." : "로그아웃"}
          </button>
        </div>
      </div>
      <dl className="grid gap-3 text-sm sm:grid-cols-2">
        <Row label="이메일" value={member.email ?? "-"} />
        <Row label="연락처" value={member.phone ?? "-"} />
        <Row
          label="기본 배송지"
          value={member.address1 ? `${member.address1} ${member.address2 ?? ""}`.trim() : "-"}
        />
      </dl>
    </section>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid grid-cols-[88px_1fr] gap-3">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="break-words text-foreground">{value}</dd>
    </div>
  );
}
