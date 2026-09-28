"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { AddressForm } from "@/components/address/AddressForm";
import { ApiError } from "@/lib/api/client";
import {
  createAddress,
  deleteAddress,
  formatAddressLine,
  listAddresses,
  setDefaultAddress,
  updateAddress,
  type AddressFormValues,
  type MemberAddress,
} from "@/features/members/api";

const MAX_ADDRESSES = 10;

type Mode = { type: "idle" } | { type: "create" } | { type: "edit"; address: MemberAddress };

export function AddressBookClient() {
  const router = useRouter();
  const [addresses, setAddresses] = useState<MemberAddress[] | null>(null);
  const [mode, setMode] = useState<Mode>({ type: "idle" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setAddresses(await listAddresses());
      setError(null);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        router.push("/login?next=/mypage/addresses");
        return;
      }
      setError(e instanceof ApiError ? e.message : "배송지를 불러오지 못했습니다.");
    }
  }, [router]);

  useEffect(() => {
    void reload();
  }, [reload]);

  async function onSave(values: AddressFormValues) {
    setBusy(true);
    setFormError(null);
    try {
      if (mode.type === "edit") {
        await updateAddress(mode.address.addressId, values);
      } else {
        await createAddress(values);
      }
      setMode({ type: "idle" });
      await reload();
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "배송지를 저장하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  async function run(action: () => Promise<unknown>, failMessage: string) {
    setBusy(true);
    setError(null);
    try {
      await action();
      await reload();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : failMessage);
    } finally {
      setBusy(false);
    }
  }

  if (addresses === null) {
    return <p className="text-sm text-muted-foreground">{error ?? "불러오는 중..."}</p>;
  }

  const canAdd = addresses.length < MAX_ADDRESSES;

  return (
    <div className="flex flex-col gap-4">
      {error ? (
        <p className="text-sm text-danger" role="alert">
          {error}
        </p>
      ) : null}

      {mode.type === "idle" ? (
        <div className="flex items-center justify-between gap-3">
          <p className="text-sm text-muted-foreground">
            {addresses.length} / {MAX_ADDRESSES}개 등록됨
          </p>
          <Button
            onClick={() => {
              setFormError(null);
              setMode({ type: "create" });
            }}
            disabled={!canAdd || busy}
          >
            새 배송지 추가
          </Button>
        </div>
      ) : (
        <section className="rounded-xl border border-border bg-surface p-5">
          <h2 className="mb-4 text-base font-semibold">
            {mode.type === "edit" ? "배송지 수정" : "새 배송지 추가"}
          </h2>
          <AddressForm
            key={mode.type === "edit" ? mode.address.addressId : "new"}
            initial={
              mode.type === "edit"
                ? {
                    label: mode.address.label,
                    receiverName: mode.address.receiverName,
                    receiverPhone: mode.address.receiverPhone,
                    postcode: mode.address.postcode,
                    address1: mode.address.address1,
                    address2: mode.address.address2 ?? "",
                    defaultAddress: mode.address.defaultAddress,
                  }
                : { defaultAddress: addresses.length === 0 }
            }
            submitLabel={mode.type === "edit" ? "수정 완료" : "배송지 저장"}
            busy={busy}
            serverError={formError}
            onSubmit={onSave}
            onCancel={() => setMode({ type: "idle" })}
          />
        </section>
      )}

      {addresses.length === 0 ? (
        <p className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          등록된 배송지가 없습니다. 자주 쓰는 배송지를 추가해 보세요.
        </p>
      ) : (
        <ul className="flex flex-col gap-3">
          {addresses.map((address) => (
            <li key={address.addressId} className="rounded-xl border border-border bg-surface p-4 text-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="flex items-center gap-2 font-medium text-foreground">
                    {address.label}
                    {address.defaultAddress ? (
                      <span className="rounded-full bg-brand px-2 py-0.5 text-[11px] font-medium text-white">
                        기본
                      </span>
                    ) : null}
                  </p>
                  <p className="mt-1">
                    {address.receiverName} · {address.receiverPhone}
                  </p>
                  <p className="mt-1 break-words text-muted-foreground">{formatAddressLine(address)}</p>
                </div>
                <div className="flex flex-wrap gap-2">
                  {!address.defaultAddress ? (
                    <button
                      type="button"
                      className="h-9 rounded-lg border border-border px-3 text-xs hover:bg-surface-soft disabled:opacity-60"
                      disabled={busy}
                      onClick={() => void run(() => setDefaultAddress(address.addressId), "기본 배송지를 변경하지 못했습니다.")}
                    >
                      기본으로 설정
                    </button>
                  ) : null}
                  <button
                    type="button"
                    className="h-9 rounded-lg border border-border px-3 text-xs hover:bg-surface-soft disabled:opacity-60"
                    disabled={busy}
                    onClick={() => {
                      setFormError(null);
                      setMode({ type: "edit", address });
                    }}
                  >
                    수정
                  </button>
                  <button
                    type="button"
                    className="h-9 rounded-lg border border-border px-3 text-xs text-danger hover:bg-surface-soft disabled:opacity-60"
                    disabled={busy}
                    onClick={() => {
                      if (!window.confirm(`'${address.label}' 배송지를 삭제할까요?`)) return;
                      void run(() => deleteAddress(address.addressId), "배송지를 삭제하지 못했습니다.");
                    }}
                  >
                    삭제
                  </button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
