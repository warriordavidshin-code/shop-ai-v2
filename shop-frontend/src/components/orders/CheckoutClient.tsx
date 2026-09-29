"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { DaumPostcodeFields } from "@/components/address/DaumPostcodeFields";
import { DeliveryFeeRow, FreeShippingNotice } from "@/components/orders/DeliveryFee";
import { formatKrw } from "@/lib/format";
import { ApiError } from "@/lib/api/client";
import { getCart, type Cart } from "@/features/cart/api";
import { createOrder, mockApprovePayment } from "@/features/orders/api";
import { getMe } from "@/features/auth/api";
import type { Member } from "@/features/auth/schemas";
import { createAddress, formatAddressLine, listAddresses, type MemberAddress } from "@/features/members/api";

const MAX_ADDRESSES = 10;

type ShippingForm = {
  receiverName: string;
  receiverPhone: string;
  postcode: string;
  address1: string;
  address2: string;
};

const EMPTY_SHIPPING: ShippingForm = {
  receiverName: "",
  receiverPhone: "",
  postcode: "",
  address1: "",
  address2: "",
};

function fromAddress(address: MemberAddress): ShippingForm {
  return {
    receiverName: address.receiverName,
    receiverPhone: address.receiverPhone,
    postcode: address.postcode,
    address1: address.address1,
    address2: address.address2 ?? "",
  };
}

function fromMember(member: Member | null): ShippingForm {
  if (!member) return EMPTY_SHIPPING;
  return {
    receiverName: member.name,
    receiverPhone: member.phone ?? "",
    postcode: member.postcode ?? "",
    address1: member.address1 ?? "",
    address2: member.address2 ?? "",
  };
}

export function CheckoutClient() {
  const router = useRouter();
  const [cart, setCart] = useState<Cart | null>(null);
  const [member, setMember] = useState<Member | null>(null);
  const [addresses, setAddresses] = useState<MemberAddress[]>([]);
  const [selected, setSelected] = useState<number | "new">("new");
  const [newAddress, setNewAddress] = useState<ShippingForm>(EMPTY_SHIPPING);
  const [saveNew, setSaveNew] = useState(false);
  const [newLabel, setNewLabel] = useState("");
  const [orderMemo, setOrderMemo] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const idempotencyKey = useMemo(
    () => (typeof crypto !== "undefined" ? crypto.randomUUID() : `key-${Date.now()}`),
    [],
  );

  useEffect(() => {
    void (async () => {
      try {
        const [cartData, me] = await Promise.all([getCart(), getMe()]);
        setCart(cartData);
        setMember(me);
        const saved = me ? await listAddresses().catch(() => [] as MemberAddress[]) : [];
        setAddresses(saved);
        const preferred = saved.find((a) => a.defaultAddress) ?? saved[0];
        if (preferred) {
          setSelected(preferred.addressId);
          setNewAddress({ ...EMPTY_SHIPPING, receiverName: me?.name ?? "", receiverPhone: me?.phone ?? "" });
        } else {
          setSelected("new");
          setNewAddress(fromMember(me));
          setSaveNew(!!me);
        }
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) {
          router.push("/login?next=/checkout");
          return;
        }
        setError(e instanceof ApiError ? e.message : "주문서를 불러오지 못했습니다.");
      }
    })();
  }, [router]);

  const selectedAddress = selected === "new" ? null : addresses.find((a) => a.addressId === selected) ?? null;
  const shipping: ShippingForm = selectedAddress ? fromAddress(selectedAddress) : newAddress;
  const canSaveNew = !!member && addresses.length < MAX_ADDRESSES;

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!cart || cart.items.length === 0) return;
    if (!shipping.receiverName.trim() || !shipping.receiverPhone.trim()) {
      setError("받는 분과 연락처를 입력해 주세요.");
      return;
    }
    if (!shipping.postcode || !shipping.address1) {
      setError("우편번호 찾기로 배송 주소를 입력해 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const order = await createOrder(
        {
          items: cart.items.map((item) => ({ skuId: item.skuId, quantity: item.quantity })),
          ...shipping,
          orderMemo,
        },
        idempotencyKey,
      );
      if (selected === "new" && saveNew && canSaveNew) {
        await createAddress({
          label: newLabel.trim() || "새 배송지",
          receiverName: shipping.receiverName.trim(),
          receiverPhone: shipping.receiverPhone.trim(),
          postcode: shipping.postcode,
          address1: shipping.address1,
          address2: shipping.address2,
          defaultAddress: addresses.length === 0,
        }).catch(() => undefined);
      }
      await mockApprovePayment(order.orderNo);
      router.push(`/order-complete/${order.orderNo}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "주문/결제에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  if (!cart) {
    return <p className="text-sm text-muted-foreground">{error ?? "불러오는 중..."}</p>;
  }

  if (cart.items.length === 0) {
    return <p className="text-sm text-muted-foreground">장바구니가 비어 있습니다.</p>;
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-8 lg:grid-cols-2">
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-lg font-semibold">배송 정보</h2>
          {member ? (
            <Link href="/mypage/addresses" className="text-sm text-brand hover:underline">
              배송지 관리
            </Link>
          ) : null}
        </div>

        {addresses.length > 0 || member ? (
          <fieldset className="flex flex-col gap-2" disabled={busy}>
            <legend className="sr-only">배송지 선택</legend>
            {addresses.map((address) => (
              <label
                key={address.addressId}
                className={[
                  "flex cursor-pointer gap-3 rounded-xl border p-3 text-sm transition-colors",
                  selected === address.addressId ? "border-brand bg-brand-soft/40" : "border-border hover:bg-surface-soft",
                ].join(" ")}
              >
                <input
                  type="radio"
                  name="shipping-address"
                  className="mt-1"
                  checked={selected === address.addressId}
                  onChange={() => setSelected(address.addressId)}
                />
                <span className="min-w-0">
                  <span className="flex items-center gap-2 font-medium">
                    {address.label}
                    {address.defaultAddress ? (
                      <span className="rounded-full bg-brand px-2 py-0.5 text-[11px] font-medium text-white">기본</span>
                    ) : null}
                  </span>
                  <span className="mt-0.5 block">
                    {address.receiverName} · {address.receiverPhone}
                  </span>
                  <span className="mt-0.5 block break-words text-muted-foreground">{formatAddressLine(address)}</span>
                </span>
              </label>
            ))}
            <label
              className={[
                "flex cursor-pointer items-center gap-3 rounded-xl border p-3 text-sm transition-colors",
                selected === "new" ? "border-brand bg-brand-soft/40" : "border-border hover:bg-surface-soft",
              ].join(" ")}
            >
              <input
                type="radio"
                name="shipping-address"
                checked={selected === "new"}
                onChange={() => setSelected("new")}
              />
              <span className="font-medium">새 배송지 입력</span>
            </label>
          </fieldset>
        ) : null}

        {selected === "new" ? (
          <div className="flex flex-col gap-3">
            {(
              [
                ["receiverName", "받는 분"],
                ["receiverPhone", "연락처"],
              ] as const
            ).map(([key, label]) => (
              <label key={key} className="flex flex-col gap-1 text-sm">
                <span>{label}</span>
                <input
                  className="h-11 rounded-xl border border-border px-3"
                  value={newAddress[key]}
                  onChange={(e) => setNewAddress((prev) => ({ ...prev, [key]: e.target.value }))}
                  disabled={busy}
                  required
                />
              </label>
            ))}
            <DaumPostcodeFields
              postcode={newAddress.postcode}
              address1={newAddress.address1}
              address2={newAddress.address2}
              onPostcodeChange={(value) => setNewAddress((prev) => ({ ...prev, postcode: value }))}
              onAddress1Change={(value) => setNewAddress((prev) => ({ ...prev, address1: value }))}
              onAddress2Change={(value) => setNewAddress((prev) => ({ ...prev, address2: value }))}
              disabled={busy}
            />
            {member ? (
              <div className="flex flex-col gap-2 rounded-xl bg-surface-soft p-3 text-sm">
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={saveNew && canSaveNew}
                    disabled={!canSaveNew || busy}
                    onChange={(e) => setSaveNew(e.target.checked)}
                  />
                  배송지 목록에 저장
                  {!canSaveNew ? (
                    <span className="text-xs text-muted-foreground">(최대 {MAX_ADDRESSES}개까지 저장 가능)</span>
                  ) : null}
                </label>
                {saveNew && canSaveNew ? (
                  <input
                    className="h-10 rounded-lg border border-border bg-surface px-3"
                    placeholder="배송지 이름 (예: 집, 회사)"
                    maxLength={50}
                    value={newLabel}
                    onChange={(e) => setNewLabel(e.target.value)}
                    disabled={busy}
                  />
                ) : null}
              </div>
            ) : null}
          </div>
        ) : null}

        <label className="flex flex-col gap-1 text-sm">
          <span>요청사항</span>
          <input
            className="h-11 rounded-xl border border-border px-3"
            value={orderMemo}
            maxLength={500}
            onChange={(e) => setOrderMemo(e.target.value)}
            disabled={busy}
          />
        </label>
      </div>

      <div className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold">주문 상품</h2>
        <ul className="flex flex-col gap-2 text-sm">
          {cart.items.map((item) => (
            <li key={item.cartItemId} className="flex justify-between gap-3 border-b border-border py-2">
              <span>
                {item.productName} / {item.optionName} × {item.quantity}
              </span>
              <span className="tabular-nums">{formatKrw(item.lineTotal)}원</span>
            </li>
          ))}
        </ul>
        <div className="rounded-xl bg-surface-soft p-4 text-sm">
          <div className="flex justify-between py-1">
            <span>상품금액</span>
            <span className="tabular-nums">{formatKrw(cart.productAmount)}원</span>
          </div>
          <DeliveryFeeRow productAmount={cart.productAmount} deliveryAmount={cart.deliveryAmount} />
          <FreeShippingNotice productAmount={cart.productAmount} className="pb-1" />
          <div className="mt-2 flex justify-between border-t border-border pt-3 font-semibold">
            <span>결제금액</span>
            <span className="tabular-nums">{formatKrw(cart.paymentAmount)}원</span>
          </div>
        </div>
        <p className="rounded-xl border border-border bg-brand-soft/50 px-3 py-2 text-sm text-foreground">
          이 결제는 개발용 Mock 결제입니다. 실제 결제가 청구되지 않습니다.
        </p>
        {error ? (
          <p className="text-sm text-danger" role="alert">
            {error}
          </p>
        ) : null}
        <Button type="submit" disabled={busy}>
          {busy ? "처리 중..." : "Mock 결제하기"}
        </Button>
        {!member ? (
          <p className="text-xs text-muted-foreground">회원 정보가 없으면 직접 입력해 주세요.</p>
        ) : null}
      </div>
    </form>
  );
}
