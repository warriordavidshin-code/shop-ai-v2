"use client";

import Link from "next/link";
import { Fragment, useState } from "react";
import { ApiError } from "@/lib/api/client";
import { formatKrw } from "@/lib/format";
import { cn } from "@/lib/utils";
import {
  addProductSku,
  parseStockInput,
  setSkuStock,
  stockSummary,
  type AdminProduct,
} from "@/features/admin/products";

type Message = { kind: "error" | "success"; text: string };
type OptionDraft = { color: string; size: string; stock: string };

const EMPTY_OPTION: OptionDraft = { color: "", size: "", stock: "0" };

export function AdminProductStockTable({
  initialProducts,
  defaultExpanded = false,
}: {
  initialProducts: AdminProduct[];
  defaultExpanded?: boolean;
}) {
  const [products, setProducts] = useState(initialProducts);
  const [expanded, setExpanded] = useState<Set<number>>(
    () => new Set(defaultExpanded ? initialProducts.map((p) => p.productId) : []),
  );
  const [drafts, setDrafts] = useState<Record<number, string>>({});
  const [optionDrafts, setOptionDrafts] = useState<Record<number, OptionDraft>>({});
  const [busyProductId, setBusyProductId] = useState<number | null>(null);
  const [messages, setMessages] = useState<Record<number, Message | undefined>>({});

  const allExpanded = products.length > 0 && products.every((p) => expanded.has(p.productId));

  function toggle(productId: number) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(productId)) next.delete(productId);
      else next.add(productId);
      return next;
    });
  }

  function toggleAll() {
    setExpanded(allExpanded ? new Set() : new Set(products.map((p) => p.productId)));
  }

  function setMessage(productId: number, message?: Message) {
    setMessages((prev) => ({ ...prev, [productId]: message }));
  }

  function changedSkus(product: AdminProduct) {
    return product.skus.filter((sku) => {
      const draft = drafts[sku.skuId];
      return draft !== undefined && draft.trim() !== String(sku.stockQuantity);
    });
  }

  async function saveStock(product: AdminProduct) {
    const targets = changedSkus(product);
    if (targets.length === 0) {
      setMessage(product.productId, { kind: "error", text: "변경된 재고가 없습니다." });
      return;
    }
    for (const sku of targets) {
      const quantity = parseStockInput(drafts[sku.skuId] ?? "");
      const label = `${sku.color} / ${sku.size}`;
      if (quantity === null) {
        setMessage(product.productId, { kind: "error", text: `${label}: 재고는 0 이상의 정수로 입력해 주세요.` });
        return;
      }
      if (quantity < sku.reservedQuantity) {
        setMessage(product.productId, {
          kind: "error",
          text: `${label}: 주문 예약 수량(${sku.reservedQuantity})보다 적게 설정할 수 없습니다.`,
        });
        return;
      }
    }

    setBusyProductId(product.productId);
    setMessage(product.productId);
    let saved = 0;
    try {
      for (const sku of targets) {
        const result = await setSkuStock(sku.skuId, parseStockInput(drafts[sku.skuId] ?? "") ?? 0);
        setProducts((prev) =>
          prev.map((p) =>
            p.productId !== product.productId
              ? p
              : {
                  ...p,
                  skus: p.skus.map((s) =>
                    s.skuId !== result.skuId
                      ? s
                      : {
                          ...s,
                          stockQuantity: result.stockQuantity,
                          reservedQuantity: result.reservedQuantity,
                          availableQuantity: result.availableQuantity,
                        },
                  ),
                },
          ),
        );
        setDrafts((prev) => {
          const next = { ...prev };
          delete next[sku.skuId];
          return next;
        });
        saved++;
      }
      setMessage(product.productId, { kind: "success", text: `${saved}개 옵션의 재고를 저장했습니다.` });
    } catch (e) {
      const reason = e instanceof ApiError ? e.message : "재고 저장에 실패했습니다.";
      setMessage(product.productId, {
        kind: "error",
        text: saved > 0 ? `${saved}개 저장 후 실패: ${reason}` : reason,
      });
    } finally {
      setBusyProductId(null);
    }
  }

  async function addOption(product: AdminProduct) {
    const draft = optionDrafts[product.productId] ?? EMPTY_OPTION;
    const quantity = parseStockInput(draft.stock);
    if (!draft.color.trim() || !draft.size.trim()) {
      setMessage(product.productId, { kind: "error", text: "색상과 사이즈를 입력해 주세요." });
      return;
    }
    if (quantity === null) {
      setMessage(product.productId, { kind: "error", text: "재고는 0 이상의 정수로 입력해 주세요." });
      return;
    }
    setBusyProductId(product.productId);
    setMessage(product.productId);
    try {
      const updated = await addProductSku(product.productId, {
        color: draft.color,
        size: draft.size,
        stockQuantity: quantity,
      });
      setProducts((prev) => prev.map((p) => (p.productId === updated.productId ? updated : p)));
      setOptionDrafts((prev) => ({ ...prev, [product.productId]: EMPTY_OPTION }));
      setMessage(product.productId, {
        kind: "success",
        text: `옵션 ${draft.color.trim()} / ${draft.size.trim()}을(를) 추가했습니다.`,
      });
    } catch (e) {
      setMessage(product.productId, {
        kind: "error",
        text: e instanceof ApiError ? e.message : "옵션 추가에 실패했습니다.",
      });
    } finally {
      setBusyProductId(null);
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex justify-end">
        <button
          type="button"
          onClick={toggleAll}
          className="rounded-lg border border-border bg-surface px-3 py-1.5 text-xs hover:bg-surface-soft"
        >
          {allExpanded ? "재고 모두 접기" : "재고 모두 펼치기"}
        </button>
      </div>
      <div className="overflow-x-auto rounded-xl border border-border bg-surface">
        <table className="min-w-full text-left text-sm">
          <thead className="bg-surface-soft text-muted-foreground">
            <tr>
              <th className="px-4 py-3">ID</th>
              <th className="px-4 py-3">상품명</th>
              <th className="px-4 py-3">상태</th>
              <th className="px-4 py-3 text-right">정상가</th>
              <th className="px-4 py-3 text-right">할인율</th>
              <th className="px-4 py-3 text-right">판매가</th>
              <th className="px-4 py-3">재고 (가용/전체)</th>
              <th className="px-4 py-3">편집</th>
            </tr>
          </thead>
          <tbody>
            {products.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-4 py-10 text-center text-muted-foreground">
                  등록된 상품이 없습니다.
                </td>
              </tr>
            ) : null}
            {products.map((product) => {
              const isOpen = expanded.has(product.productId);
              const summary = stockSummary(product.skus);
              const busy = busyProductId === product.productId;
              const message = messages[product.productId];
              const option = optionDrafts[product.productId] ?? EMPTY_OPTION;
              const pending = changedSkus(product).length;
              return (
                <Fragment key={product.productId}>
                  <tr className="border-t border-border">
                    <td className="px-4 py-3">{product.productId}</td>
                    <td className="px-4 py-3">{product.productName}</td>
                    <td className="px-4 py-3">{product.status}</td>
                    <td className="px-4 py-3 text-right">{formatKrw(product.normalPrice)}원</td>
                    <td className="px-4 py-3 text-right">
                      {product.discountRate > 0 ? (
                        <span className="font-medium text-danger">{product.discountRate}%</span>
                      ) : (
                        <span className="text-muted-foreground">-</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right font-medium">{formatKrw(product.salePrice)}원</td>
                    <td className="px-4 py-3">
                      <button
                        type="button"
                        onClick={() => toggle(product.productId)}
                        aria-expanded={isOpen}
                        className={cn(
                          "inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1 text-xs",
                          product.skus.length === 0
                            ? "border-accent/40 bg-accent-soft"
                            : summary.available === 0
                              ? "border-danger/40 text-danger"
                              : "border-border hover:bg-surface-soft",
                        )}
                      >
                        {product.skus.length === 0
                          ? "옵션 없음"
                          : `${summary.available} / ${summary.stock}개 · 옵션 ${product.skus.length}`}
                        <span aria-hidden>{isOpen ? "▴" : "▾"}</span>
                      </button>
                    </td>
                    <td className="px-4 py-3">
                      <Link
                        href={`/admin/products/${product.productId}/edit`}
                        className="text-brand hover:underline"
                      >
                        편집
                      </Link>
                    </td>
                  </tr>
                  {isOpen ? (
                    <tr className="border-t border-border bg-surface-soft/60">
                      <td colSpan={8} className="px-4 py-4">
                        <div className="flex flex-col gap-3">
                          {product.skus.length > 0 ? (
                            <div className="overflow-x-auto rounded-lg border border-border bg-surface">
                              <table className="min-w-full text-left text-xs">
                                <thead className="bg-surface-soft text-muted-foreground">
                                  <tr>
                                    <th className="px-3 py-2">옵션 (색상 / 사이즈)</th>
                                    <th className="px-3 py-2">SKU 코드</th>
                                    <th className="px-3 py-2">상태</th>
                                    <th className="px-3 py-2">재고 수량</th>
                                    <th className="px-3 py-2 text-right">주문 예약</th>
                                    <th className="px-3 py-2 text-right">판매 가능</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {product.skus.map((sku) => {
                                    const draft = drafts[sku.skuId];
                                    const value = draft ?? String(sku.stockQuantity);
                                    const dirty = draft !== undefined && draft.trim() !== String(sku.stockQuantity);
                                    const invalid = draft !== undefined && parseStockInput(draft) === null;
                                    return (
                                      <tr key={sku.skuId} className="border-t border-border">
                                        <td className="px-3 py-2">
                                          {sku.color} / {sku.size}
                                        </td>
                                        <td className="px-3 py-2 text-muted-foreground">{sku.skuCode}</td>
                                        <td className="px-3 py-2">{sku.status}</td>
                                        <td className="px-3 py-2">
                                          <input
                                            type="number"
                                            min={sku.reservedQuantity}
                                            step={1}
                                            inputMode="numeric"
                                            aria-label={`${sku.color} ${sku.size} 재고 수량`}
                                            className={cn(
                                              "h-9 w-24 rounded-lg border bg-surface px-2 text-right",
                                              invalid ? "border-danger" : dirty ? "border-brand" : "border-border",
                                            )}
                                            value={value}
                                            disabled={busy}
                                            onChange={(e) =>
                                              setDrafts((prev) => ({ ...prev, [sku.skuId]: e.target.value }))
                                            }
                                            onKeyDown={(e) => {
                                              if (e.key === "Enter") {
                                                e.preventDefault();
                                                void saveStock(product);
                                              }
                                            }}
                                          />
                                        </td>
                                        <td className="px-3 py-2 text-right">{sku.reservedQuantity}</td>
                                        <td
                                          className={cn(
                                            "px-3 py-2 text-right font-medium",
                                            sku.availableQuantity === 0 && "text-danger",
                                          )}
                                        >
                                          {sku.availableQuantity}
                                        </td>
                                      </tr>
                                    );
                                  })}
                                </tbody>
                              </table>
                            </div>
                          ) : (
                            <p className="text-xs text-muted-foreground">
                              등록된 옵션이 없어 판매할 수 없습니다. 아래에서 색상/사이즈 옵션과 재고를 추가해 주세요.
                            </p>
                          )}

                          <div className="flex flex-wrap items-end justify-between gap-3">
                            <div className="flex flex-wrap items-end gap-2">
                              <label className="flex flex-col gap-1 text-xs">
                                <span className="text-muted-foreground">색상</span>
                                <input
                                  className="h-9 w-28 rounded-lg border border-border bg-surface px-2"
                                  value={option.color}
                                  disabled={busy}
                                  onChange={(e) =>
                                    setOptionDrafts((prev) => ({
                                      ...prev,
                                      [product.productId]: { ...option, color: e.target.value },
                                    }))
                                  }
                                />
                              </label>
                              <label className="flex flex-col gap-1 text-xs">
                                <span className="text-muted-foreground">사이즈</span>
                                <input
                                  className="h-9 w-20 rounded-lg border border-border bg-surface px-2"
                                  value={option.size}
                                  disabled={busy}
                                  onChange={(e) =>
                                    setOptionDrafts((prev) => ({
                                      ...prev,
                                      [product.productId]: { ...option, size: e.target.value },
                                    }))
                                  }
                                />
                              </label>
                              <label className="flex flex-col gap-1 text-xs">
                                <span className="text-muted-foreground">재고</span>
                                <input
                                  type="number"
                                  min={0}
                                  step={1}
                                  className="h-9 w-20 rounded-lg border border-border bg-surface px-2 text-right"
                                  value={option.stock}
                                  disabled={busy}
                                  onChange={(e) =>
                                    setOptionDrafts((prev) => ({
                                      ...prev,
                                      [product.productId]: { ...option, stock: e.target.value },
                                    }))
                                  }
                                />
                              </label>
                              <button
                                type="button"
                                disabled={busy}
                                onClick={() => void addOption(product)}
                                className="h-9 rounded-lg border border-border bg-surface px-3 text-xs hover:bg-surface-soft disabled:opacity-60"
                              >
                                옵션 추가
                              </button>
                            </div>
                            {product.skus.length > 0 ? (
                              <button
                                type="button"
                                disabled={busy || pending === 0}
                                onClick={() => void saveStock(product)}
                                className="h-9 rounded-lg bg-brand px-4 text-xs font-medium text-white hover:bg-brand-hover disabled:opacity-60"
                              >
                                {busy ? "저장 중..." : pending > 0 ? `재고 저장 (${pending})` : "재고 저장"}
                              </button>
                            ) : null}
                          </div>

                          {message ? (
                            <p
                              role={message.kind === "error" ? "alert" : "status"}
                              className={cn("text-xs", message.kind === "error" ? "text-danger" : "text-success")}
                            >
                              {message.text}
                            </p>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  ) : null}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
