"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { DaumPostcodeFields } from "@/components/address/DaumPostcodeFields";
import { addressFormSchema, type AddressFormValues } from "@/features/members/api";

const inputClass =
  "h-11 w-full rounded-xl border border-border bg-surface px-3 text-foreground outline-none focus-visible:ring-2 focus-visible:ring-brand";

export const EMPTY_ADDRESS: AddressFormValues = {
  label: "",
  receiverName: "",
  receiverPhone: "",
  postcode: "",
  address1: "",
  address2: "",
  defaultAddress: false,
};

type Props = {
  initial?: Partial<AddressFormValues>;
  submitLabel: string;
  busy?: boolean;
  serverError?: string | null;
  onSubmit: (values: AddressFormValues) => void | Promise<void>;
  onCancel?: () => void;
};

export function AddressForm({ initial, submitLabel, busy, serverError, onSubmit, onCancel }: Props) {
  const [values, setValues] = useState<AddressFormValues>({ ...EMPTY_ADDRESS, ...initial });
  const [errors, setErrors] = useState<Partial<Record<keyof AddressFormValues, string>>>({});

  function set<K extends keyof AddressFormValues>(key: K, value: AddressFormValues[K]) {
    setValues((prev) => ({ ...prev, [key]: value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const parsed = addressFormSchema.safeParse(values);
    if (!parsed.success) {
      const next: Partial<Record<keyof AddressFormValues, string>> = {};
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as keyof AddressFormValues;
        next[key] ??= issue.message;
      }
      setErrors(next);
      return;
    }
    setErrors({});
    await onSubmit(parsed.data);
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
      <div className="grid gap-4 sm:grid-cols-3">
        <Field label="배송지 이름" error={errors.label}>
          <input
            className={inputClass}
            value={values.label}
            placeholder="예: 집, 회사"
            maxLength={50}
            onChange={(e) => set("label", e.target.value)}
            disabled={busy}
          />
        </Field>
        <Field label="받는 분" error={errors.receiverName}>
          <input
            className={inputClass}
            value={values.receiverName}
            maxLength={100}
            onChange={(e) => set("receiverName", e.target.value)}
            disabled={busy}
          />
        </Field>
        <Field label="연락처" error={errors.receiverPhone}>
          <input
            className={inputClass}
            value={values.receiverPhone}
            inputMode="tel"
            placeholder="010-0000-0000"
            maxLength={32}
            onChange={(e) => set("receiverPhone", e.target.value)}
            disabled={busy}
          />
        </Field>
      </div>
      <DaumPostcodeFields
        postcode={values.postcode}
        address1={values.address1}
        address2={values.address2 ?? ""}
        onPostcodeChange={(v) => set("postcode", v)}
        onAddress1Change={(v) => set("address1", v)}
        onAddress2Change={(v) => set("address2", v)}
        errors={{ postcode: errors.postcode, address1: errors.address1, address2: errors.address2 }}
        disabled={busy}
      />
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={values.defaultAddress}
          onChange={(e) => set("defaultAddress", e.target.checked)}
          disabled={busy}
        />
        기본 배송지로 설정
      </label>
      {serverError ? (
        <p className="text-sm text-danger" role="alert">
          {serverError}
        </p>
      ) : null}
      <div className="flex gap-2">
        <Button type="submit" disabled={busy}>
          {busy ? "저장 중..." : submitLabel}
        </Button>
        {onCancel ? (
          <Button variant="secondary" onClick={onCancel} disabled={busy}>
            취소
          </Button>
        ) : null}
      </div>
    </form>
  );
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-1.5 text-sm">
      <span className="font-medium text-foreground">{label}</span>
      {children}
      {error ? <span className="text-sm text-danger">{error}</span> : null}
    </label>
  );
}
