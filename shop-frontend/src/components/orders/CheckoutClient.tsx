"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { DaumPostcodeFields } from "@/components/address/DaumPostcodeFields";
import { formatKrw } from "@/lib/format";
import { ApiError } from "@/lib/api/client";
import { getCart, type Cart } from "@/features/cart/api";
import { createOrder, mockApprovePayment } from "@/features/orders/api";
import { getMe } from "@/features/auth/api";
import type { Member } from "@/features/auth/schemas";

export function CheckoutClient() {
  const router = useRouter();
  const [cart, setCart] = useState<Cart | null>(null);
  const [member, setMember] = useState<Member | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({
    receiverName: "",
    receiverPhone: "",
    postcode: "",
    address1: "",
    address2: "",
    orderMemo: "",
  });

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
        if (me) {
          setForm((prev) => ({
            ...prev,
            receiverName: me.name,
            receiverPhone: me.phone,
            postcode: me.postcode,
            address1: me.address1,
            address2: me.address2 ?? "",
          }));
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

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!cart || cart.items.length === 0) return;
    if (!form.postcode || !form.address1) {
      setError("우편번호 찾기로 배송 주소를 입력해 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const order = await createOrder(
        {
          items: cart.items.map((item) => ({ skuId: item.skuId, quantity: item.quantity })),
          ...form,
        },
        idempotencyKey,
      );
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
      <div className="flex flex-col gap-3">
        <h2 className="text-lg font-semibold">배송 정보</h2>
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
              value={form[key]}
              onChange={(e) => setForm((prev) => ({ ...prev, [key]: e.target.value }))}
              required
            />
          </label>
        ))}
        <DaumPostcodeFields
          postcode={form.postcode}
          address1={form.address1}
          address2={form.address2}
          onPostcodeChange={(value) => setForm((prev) => ({ ...prev, postcode: value }))}
          onAddress1Change={(value) => setForm((prev) => ({ ...prev, address1: value }))}
          onAddress2Change={(value) => setForm((prev) => ({ ...prev, address2: value }))}
          disabled={busy}
        />
        <label className="flex flex-col gap-1 text-sm">
          <span>요청사항</span>
          <input
            className="h-11 rounded-xl border border-border px-3"
            value={form.orderMemo}
            onChange={(e) => setForm((prev) => ({ ...prev, orderMemo: e.target.value }))}
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
          <div className="flex justify-between py-1">
            <span>배송비</span>
            <span className="tabular-nums">{formatKrw(cart.deliveryAmount)}원</span>
          </div>
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
