"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { ImageRollingGallery } from "@/components/ui/ImageRollingGallery";
import { Price } from "@/components/ui/Price";
import { ProductHtmlDescription } from "@/components/product/ProductHtmlDescription";
import type { ProductDetail } from "@/features/products/api";
import { addCartItem, readGuestCart, writeGuestCart } from "@/features/cart/api";
import { addWishlist } from "@/features/wishlist/api";
import { ApiError } from "@/lib/api/client";

export function ProductDetailClient({ product }: { product: ProductDetail }) {
  const router = useRouter();
  const colors = useMemo(
    () => Array.from(new Set(product.skus.map((sku) => sku.color))),
    [product.skus],
  );
  const [color, setColor] = useState(colors[0] ?? "");
  const sizes = useMemo(
    () => product.skus.filter((sku) => sku.color === color).map((sku) => sku.size),
    [product.skus, color],
  );
  const [size, setSize] = useState(sizes[0] ?? "");
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const selectedSku = product.skus.find((sku) => sku.color === color && sku.size === size);
  const unavailable = !selectedSku || selectedSku.availableQuantity <= 0;

  async function handleAddCart() {
    if (!selectedSku || unavailable) return;
    setBusy(true);
    setMessage(null);
    try {
      await addCartItem(selectedSku.skuId, 1);
      setMessage("장바구니에 담았습니다.");
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        const guest = readGuestCart();
        const existing = guest.find((item) => item.skuId === selectedSku.skuId);
        if (existing) existing.quantity += 1;
        else guest.push({ skuId: selectedSku.skuId, quantity: 1 });
        writeGuestCart(guest);
        setMessage("비회원 장바구니에 담았습니다. 로그인 시 병합됩니다.");
      } else {
        setMessage(e instanceof ApiError ? e.message : "장바구니 담기에 실패했습니다.");
      }
    } finally {
      setBusy(false);
    }
  }

  async function handleWishlist() {
    setBusy(true);
    setMessage(null);
    try {
      await addWishlist(product.productId);
      setMessage("찜 목록에 추가했습니다.");
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        router.push(`/login?next=/products/${product.productId}`);
      } else {
        setMessage(e instanceof ApiError ? e.message : "찜 추가에 실패했습니다.");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid gap-8 lg:grid-cols-2">
      <div>
        <ImageRollingGallery
          images={(product.images ?? [])
            .filter((img) => !!img.imageUrl)
            .sort((a, b) => {
              const aMain = a.imageType === "MAIN" ? 0 : 1;
              const bMain = b.imageType === "MAIN" ? 0 : 1;
              if (aMain !== bMain) return aMain - bMain;
              return a.sortOrder - b.sortOrder;
            })
            .map((img) => ({ url: img.imageUrl!, alt: img.altText ?? product.productName }))}
          placeholderLabel={product.brandName}
        />
      </div>

      <div className="flex flex-col gap-5">
        <div>
          <p className="text-sm text-muted-foreground">{product.brandName}</p>
          <h1 className="mt-1 text-2xl font-semibold text-foreground">{product.productName}</h1>
          {product.summary ? (
            <p className="mt-2 text-sm text-muted-foreground">{product.summary}</p>
          ) : null}
        </div>

        <Price
          salePrice={product.salePrice}
          normalPrice={product.normalPrice}
          discountRate={product.discountRate}
        />

        <fieldset className="flex flex-col gap-2">
          <legend className="text-sm font-medium">색상</legend>
          <div className="flex flex-wrap gap-2">
            {colors.map((item) => (
              <button
                key={item}
                type="button"
                onClick={() => {
                  setColor(item);
                  const nextSizes = product.skus
                    .filter((sku) => sku.color === item)
                    .map((sku) => sku.size);
                  setSize(nextSizes[0] ?? "");
                }}
                className={`inline-flex h-11 items-center rounded-xl border px-4 text-sm ${
                  color === item
                    ? "border-brand bg-brand-soft"
                    : "border-border bg-surface hover:bg-surface-soft"
                }`}
              >
                {item}
              </button>
            ))}
          </div>
        </fieldset>

        <fieldset className="flex flex-col gap-2">
          <legend className="text-sm font-medium">사이즈</legend>
          <div className="flex flex-wrap gap-2">
            {sizes.map((item) => {
              const sku = product.skus.find((s) => s.color === color && s.size === item);
              const disabled = !sku || sku.availableQuantity <= 0;
              return (
                <button
                  key={item}
                  type="button"
                  disabled={disabled}
                  onClick={() => setSize(item)}
                  aria-disabled={disabled}
                  className={`inline-flex h-11 min-w-11 items-center justify-center rounded-xl border px-4 text-sm disabled:cursor-not-allowed disabled:opacity-40 ${
                    size === item
                      ? "border-brand bg-brand-soft"
                      : "border-border bg-surface hover:bg-surface-soft"
                  }`}
                >
                  {item}
                  {disabled ? " (품절)" : ""}
                </button>
              );
            })}
          </div>
        </fieldset>

        <p className="text-sm text-muted-foreground">
          {selectedSku
            ? `판매 가능 수량 ${selectedSku.availableQuantity}`
            : "옵션을 선택해 주세요"}
        </p>

        <div className="flex flex-wrap gap-3">
          <Button disabled={unavailable || busy} onClick={handleAddCart}>
            장바구니
          </Button>
          <Button
            variant="secondary"
            disabled={unavailable || busy}
            onClick={async () => {
              await handleAddCart();
              router.push("/checkout");
            }}
          >
            바로구매
          </Button>
          <Button variant="ghost" disabled={busy} onClick={handleWishlist}>
            찜
          </Button>
        </div>
        {message ? (
          <p className="text-sm text-foreground" role="status">
            {message}
          </p>
        ) : null}
        <p className="text-xs text-muted-foreground">
          표시 가격은 예상이며, 주문 금액은 서버에서 다시 계산됩니다.
        </p>

        <section className="rounded-xl border border-border bg-surface p-4 text-sm">
          <h2 className="mb-2 font-semibold">상품 정보</h2>
          <ul className="grid gap-1 text-muted-foreground">
            <li>소재: {product.material ?? "-"}</li>
            <li>핏: {product.fitType ?? "-"}</li>
            <li>시즌: {product.season ?? "-"}</li>
          </ul>
        </section>

        {product.measurements.length > 0 ? (
          <section className="overflow-x-auto rounded-xl border border-border">
            <h2 className="border-b border-border px-4 py-3 text-sm font-semibold">실측표 (cm)</h2>
            <table className="min-w-full text-left text-sm">
              <thead className="bg-surface-soft text-muted-foreground">
                <tr>
                  <th className="px-3 py-2">사이즈</th>
                  <th className="px-3 py-2">어깨</th>
                  <th className="px-3 py-2">가슴</th>
                  <th className="px-3 py-2">허리</th>
                  <th className="px-3 py-2">총장</th>
                </tr>
              </thead>
              <tbody>
                {product.measurements.map((row) => (
                  <tr key={row.measurementId} className="border-t border-border">
                    <td className="px-3 py-2">{row.size}</td>
                    <td className="px-3 py-2">{row.shoulder ?? "-"}</td>
                    <td className="px-3 py-2">{row.chest ?? "-"}</td>
                    <td className="px-3 py-2">{row.waist ?? "-"}</td>
                    <td className="px-3 py-2">{row.totalLength ?? "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        ) : null}

        {product.description ? (
          <section className="text-sm leading-relaxed text-muted-foreground">
            <h2 className="mb-2 font-semibold text-foreground">상세설명</h2>
            <ProductHtmlDescription html={product.description} />
          </section>
        ) : null}
      </div>
    </div>
  );
}
