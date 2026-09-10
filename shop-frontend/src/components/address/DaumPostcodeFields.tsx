"use client";

import { useEffect, useRef, useState } from "react";
import { loadDaumPostcode, selectedAddress } from "@/lib/daum-postcode";

const inputClass =
  "h-11 w-full rounded-xl border border-border bg-surface px-3 text-foreground outline-none focus-visible:ring-2 focus-visible:ring-brand";

type Props = {
  postcode: string;
  address1: string;
  address2: string;
  onPostcodeChange: (value: string) => void;
  onAddress1Change: (value: string) => void;
  onAddress2Change: (value: string) => void;
  errors?: {
    postcode?: string;
    address1?: string;
    address2?: string;
  };
  disabled?: boolean;
};

export function DaumPostcodeFields({
  postcode,
  address1,
  address2,
  onPostcodeChange,
  onAddress1Change,
  onAddress2Change,
  errors,
  disabled,
}: Props) {
  const address2Ref = useRef<HTMLInputElement>(null);
  const layerRef = useRef<HTMLDivElement>(null);
  const onPostcodeChangeRef = useRef(onPostcodeChange);
  const onAddress1ChangeRef = useRef(onAddress1Change);
  const [open, setOpen] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  onPostcodeChangeRef.current = onPostcodeChange;
  onAddress1ChangeRef.current = onAddress1Change;

  useEffect(() => {
    if (!open) {
      return;
    }
    let cancelled = false;

    void (async () => {
      try {
        const Postcode = await loadDaumPostcode();
        if (cancelled || !layerRef.current) {
          return;
        }
        layerRef.current.replaceChildren();
        new Postcode({
          oncomplete(data) {
            onPostcodeChangeRef.current(data.zonecode);
            onAddress1ChangeRef.current(selectedAddress(data));
            setOpen(false);
            window.setTimeout(() => address2Ref.current?.focus(), 0);
          },
          onclose() {
            setOpen(false);
          },
          width: "100%",
          height: "100%",
        }).embed(layerRef.current);
      } catch {
        if (!cancelled) {
          setLoadError("우편번호 서비스를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
          setOpen(false);
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [open]);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <span className="text-sm font-medium text-foreground">우편번호</span>
        <div className="flex gap-2">
          <input
            className={inputClass}
            value={postcode}
            readOnly
            placeholder="우편번호 찾기를 눌러 주세요"
            aria-invalid={!!errors?.postcode}
            onChange={(e) => onPostcodeChange(e.target.value)}
          />
          <button
            type="button"
            onClick={() => {
              setLoadError(null);
              setOpen(true);
            }}
            disabled={disabled}
            className="inline-flex h-11 shrink-0 items-center justify-center rounded-xl border border-border bg-surface px-4 text-sm font-medium text-foreground hover:bg-surface-soft disabled:opacity-60"
          >
            우편번호 찾기
          </button>
        </div>
        {errors?.postcode ? <p className="text-sm text-danger">{errors.postcode}</p> : null}
        {loadError ? <p className="text-sm text-danger">{loadError}</p> : null}
      </div>
      <div className="flex flex-col gap-1.5">
        <span className="text-sm font-medium text-foreground">기본주소</span>
        <input
          className={inputClass}
          value={address1}
          readOnly
          placeholder="우편번호 찾기로 입력됩니다"
          aria-invalid={!!errors?.address1}
          onChange={(e) => onAddress1Change(e.target.value)}
        />
        {errors?.address1 ? <p className="text-sm text-danger">{errors.address1}</p> : null}
      </div>
      <div className="flex flex-col gap-1.5">
        <span className="text-sm font-medium text-foreground">상세주소</span>
        <input
          ref={address2Ref}
          className={inputClass}
          value={address2}
          placeholder="상세주소를 입력해 주세요"
          aria-invalid={!!errors?.address2}
          disabled={disabled}
          onChange={(e) => onAddress2Change(e.target.value)}
        />
        {errors?.address2 ? <p className="text-sm text-danger">{errors.address2}</p> : null}
      </div>

      {open ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-label="우편번호 검색"
        >
          <div className="flex h-[min(520px,90vh)] w-full max-w-[500px] flex-col overflow-hidden rounded-xl bg-surface shadow-lg">
            <div className="flex items-center justify-between border-b border-border px-4 py-3">
              <h3 className="text-sm font-semibold">우편번호 찾기</h3>
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="rounded-lg px-2 py-1 text-sm text-muted-foreground hover:bg-surface-soft"
              >
                닫기
              </button>
            </div>
            <div ref={layerRef} className="min-h-0 flex-1 bg-white">
              <p className="p-4 text-sm text-muted-foreground">우편번호 서비스를 불러오는 중...</p>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
