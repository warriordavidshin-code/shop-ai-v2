"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { parseApiError, shopFetch } from "@/lib/api/client";

export function InventoryAdjustForm() {
  const [skuId, setSkuId] = useState("1");
  const [delta, setDelta] = useState("5");
  const [reason, setReason] = useState("입고");
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      const response = await shopFetch(`/admin/inventories/${skuId}/adjust`, {
        method: "PATCH",
        body: JSON.stringify({
          quantityDelta: Number(delta),
          reason,
        }),
      });
      if (!response.ok) throw await parseApiError(response);
      setMessage("재고가 조정되었습니다.");
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "재고 조정 실패");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="flex max-w-md flex-col gap-3">
      <label className="text-sm">
        SKU ID
        <input
          className="mt-1 h-11 w-full rounded-xl border border-border px-3"
          value={skuId}
          onChange={(e) => setSkuId(e.target.value)}
        />
      </label>
      <label className="text-sm">
        수량 증감
        <input
          className="mt-1 h-11 w-full rounded-xl border border-border px-3"
          value={delta}
          onChange={(e) => setDelta(e.target.value)}
        />
      </label>
      <label className="text-sm">
        사유
        <input
          className="mt-1 h-11 w-full rounded-xl border border-border px-3"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </label>
      <Button type="submit" disabled={busy}>
        {busy ? "처리 중..." : "재고 조정"}
      </Button>
      {message ? <p className="text-sm">{message}</p> : null}
    </form>
  );
}
