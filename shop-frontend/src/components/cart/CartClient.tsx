"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";
import { formatKrw } from "@/lib/format";
import { ApiError } from "@/lib/api/client";
import {
  getCart,
  mergeGuestCart,
  removeCartItem,
  updateCartItem,
  type Cart,
} from "@/features/cart/api";

export function CartClient() {
  const [cart, setCart] = useState<Cart | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      try {
        await mergeGuestCart();
      } catch {
        // ignore merge errors for empty/unauthorized guest cart
      }
      const data = await getCart();
      setCart(data);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        setError("로그인 후 장바구니를 확인할 수 있습니다.");
      } else if (e instanceof ApiError) {
        setError(e.message);
      } else {
        setError("장바구니를 불러오지 못했습니다.");
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  if (loading) {
    return <p className="text-sm text-muted-foreground">장바구니를 불러오는 중...</p>;
  }

  if (error) {
    return (
      <div className="flex flex-col gap-3">
        <p className="text-sm text-danger" role="alert">
          {error}
        </p>
        <Link href="/login?next=/cart" className="text-sm text-brand hover:underline">
          로그인하기
        </Link>
      </div>
    );
  }

  if (!cart || cart.items.length === 0) {
    return (
      <p className="rounded-xl border border-border bg-surface-soft px-4 py-10 text-center text-sm text-muted-foreground">
        장바구니가 비어 있습니다.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <ul className="flex flex-col gap-4">
        {cart.items.map((item) => (
          <li
            key={item.cartItemId}
            className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4 md:flex-row md:items-center md:justify-between"
          >
            <div>
              <p className="font-medium text-foreground">{item.productName}</p>
              <p className="text-sm text-muted-foreground">{item.optionName}</p>
              <p className="mt-1 text-sm tabular-nums">{formatKrw(item.unitPrice)}원</p>
              <p className="text-xs text-muted-foreground">
                재고 {item.availableQuantity} · 합계 {formatKrw(item.lineTotal)}원
              </p>
            </div>
            <div className="flex items-center gap-2">
              <label className="sr-only" htmlFor={`qty-${item.cartItemId}`}>
                수량
              </label>
              <input
                id={`qty-${item.cartItemId}`}
                type="number"
                min={1}
                max={item.availableQuantity}
                defaultValue={item.quantity}
                className="h-11 w-20 rounded-xl border border-border px-3"
                onBlur={async (e) => {
                  const quantity = Number(e.target.value);
                  if (!Number.isFinite(quantity) || quantity < 1) return;
                  try {
                    setCart(await updateCartItem(item.cartItemId, quantity));
                  } catch (err) {
                    setError(err instanceof ApiError ? err.message : "수량 변경 실패");
                    await load();
                  }
                }}
              />
              <Button
                variant="secondary"
                onClick={async () => {
                  await removeCartItem(item.cartItemId);
                  await load();
                }}
              >
                삭제
              </Button>
            </div>
          </li>
        ))}
      </ul>

      <div className="rounded-xl border border-border bg-surface-soft p-4 text-sm">
        <div className="flex justify-between py-1">
          <span>상품금액</span>
          <span className="tabular-nums">{formatKrw(cart.productAmount)}원</span>
        </div>
        <div className="flex justify-between py-1">
          <span>배송비</span>
          <span className="tabular-nums">{formatKrw(cart.deliveryAmount)}원</span>
        </div>
        <div className="mt-2 flex justify-between border-t border-border pt-3 text-base font-semibold">
          <span>결제금액(예상)</span>
          <span className="tabular-nums">{formatKrw(cart.paymentAmount)}원</span>
        </div>
        <p className="mt-2 text-xs text-muted-foreground">
          최종 결제금액은 주문 시 서버에서 다시 계산됩니다.
        </p>
        <Link
          href="/checkout"
          className="mt-4 inline-flex h-11 items-center justify-center rounded-xl bg-brand px-5 text-sm font-medium text-white hover:bg-brand-hover"
        >
          주문하기
        </Link>
      </div>
    </div>
  );
}
