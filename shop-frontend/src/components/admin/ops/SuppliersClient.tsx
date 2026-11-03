"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  adminJson,
  errorMessage,
  postJson,
  type Supplier,
  type SupplierMapping,
  won,
} from "@/features/admin/ops";
import {
  Badge,
  buttonClass,
  Field,
  inputClass,
  Notice,
  PageHeader,
  primaryButtonClass,
  Section,
  Table,
} from "@/components/admin/ops/ui";

type SupplierForm = {
  supplierName: string;
  businessNo: string;
  contactName: string;
  phone: string;
  address: string;
  leadDays: string;
  memo: string;
  active: boolean;
};

const EMPTY: SupplierForm = {
  supplierName: "",
  businessNo: "",
  contactName: "",
  phone: "",
  address: "",
  leadDays: "",
  memo: "",
  active: true,
};

export function SuppliersClient() {
  const [list, setList] = useState<Supplier[]>([]);
  const [editing, setEditing] = useState<number | "new" | null>(null);
  const [form, setForm] = useState<SupplierForm>(EMPTY);
  const [mappings, setMappings] = useState<{ supplierId: number; rows: SupplierMapping[] } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setList(await adminJson<Supplier[]>("/admin/suppliers"));
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  function edit(supplier: Supplier | null) {
    setError(null);
    if (!supplier) {
      setEditing("new");
      setForm(EMPTY);
      return;
    }
    setEditing(supplier.id);
    setForm({
      supplierName: supplier.supplierName,
      businessNo: supplier.businessNo ?? "",
      contactName: supplier.contactName ?? "",
      phone: supplier.phone ?? "",
      address: supplier.address ?? "",
      leadDays: supplier.leadDays === null ? "" : String(supplier.leadDays),
      memo: supplier.memo ?? "",
      active: supplier.active,
    });
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    const body = {
      ...form,
      businessNo: form.businessNo || null,
      contactName: form.contactName || null,
      phone: form.phone || null,
      address: form.address || null,
      memo: form.memo || null,
      leadDays: form.leadDays === "" ? null : Number(form.leadDays),
    };
    try {
      if (editing === "new") {
        await postJson("/admin/suppliers", body);
      } else if (editing !== null) {
        await postJson(`/admin/suppliers/${editing}`, body, "PUT");
      }
      setEditing(null);
      void load();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function showMappings(id: number) {
    try {
      setMappings({ supplierId: id, rows: await adminJson<SupplierMapping[]>(`/admin/suppliers/${id}/mappings`) });
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  async function deleteMapping(supplierId: number, mappingId: number) {
    if (!window.confirm("이 자동 매칭 기억을 삭제할까요? 다음 매입부터는 다시 후보 확인이 필요합니다.")) return;
    try {
      await adminJson(`/admin/suppliers/${supplierId}/mappings/${mappingId}`, { method: "DELETE" });
      void showMappings(supplierId);
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  const set = (key: keyof SupplierForm) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [key]: e.target.value }));

  return (
    <>
      <PageHeader
        title="거래처 관리"
        description="리드타임은 재고 부족 분석과 발주 추천에 사용됩니다. 비워 두면 실제 입고 이력의 평균, 그것도 없으면 가격 정책의 기본값을 씁니다."
        actions={
          <button type="button" className={primaryButtonClass} onClick={() => edit(null)}>
            거래처 추가
          </button>
        }
      />
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {editing !== null ? (
        <Section title={editing === "new" ? "거래처 추가" : "거래처 수정"}>
          <form onSubmit={save} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="거래처명 *">
              <input required className={inputClass} value={form.supplierName} onChange={set("supplierName")} />
            </Field>
            <Field label="사업자번호">
              <input className={inputClass} value={form.businessNo} onChange={set("businessNo")} />
            </Field>
            <Field label="담당자">
              <input className={inputClass} value={form.contactName} onChange={set("contactName")} />
            </Field>
            <Field label="연락처">
              <input className={inputClass} value={form.phone} onChange={set("phone")} />
            </Field>
            <Field label="주소">
              <input className={inputClass} value={form.address} onChange={set("address")} />
            </Field>
            <Field label="리드타임(일)">
              <input className={inputClass} inputMode="numeric" value={form.leadDays} onChange={set("leadDays")} />
            </Field>
            <Field label="메모">
              <input className={inputClass} value={form.memo} onChange={set("memo")} />
            </Field>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={form.active} onChange={(e) => setForm((prev) => ({ ...prev, active: e.target.checked }))} />
              거래 중
            </label>
            <div className="flex gap-2 sm:col-span-2 lg:col-span-4">
              <button type="submit" className={primaryButtonClass}>
                저장
              </button>
              <button type="button" className={buttonClass} onClick={() => setEditing(null)}>
                취소
              </button>
            </div>
          </form>
        </Section>
      ) : null}
      <Table>
        <thead>
          <tr>
            <th>거래처</th>
            <th>사업자번호</th>
            <th>담당자 / 연락처</th>
            <th className="text-right">리드타임</th>
            <th className="text-right">매입 건수</th>
            <th className="text-right">매입액</th>
            <th className="text-right">매칭 기억</th>
            <th>상태</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {list.length === 0 ? (
            <tr>
              <td colSpan={9} className="py-8 text-center text-muted-foreground">
                거래처가 없습니다. 매입장부에 거래처명을 입력하면 자동으로 등록됩니다.
              </td>
            </tr>
          ) : null}
          {list.map((s) => (
            <tr key={s.id}>
              <td>
                <Link className="underline" href={`/admin/purchases?supplier=${encodeURIComponent(s.supplierName)}`}>
                  {s.supplierName}
                </Link>
              </td>
              <td>{s.businessNo ?? "-"}</td>
              <td>{[s.contactName, s.phone].filter(Boolean).join(" / ") || "-"}</td>
              <td className="text-right tabular-nums">
                {s.leadDays ?? "-"}
                {s.averageLeadDays !== null ? <span className="text-xs text-muted-foreground"> (실측 {Number(s.averageLeadDays).toFixed(1)})</span> : null}
              </td>
              <td className="text-right tabular-nums">{s.purchaseCount}</td>
              <td className="text-right tabular-nums">{won(s.purchaseAmount)}</td>
              <td className="text-right tabular-nums">
                <button type="button" className="underline" onClick={() => void showMappings(s.id)}>
                  {s.mappedProductCount}
                </button>
              </td>
              <td>{s.active ? <Badge tone="ok">거래 중</Badge> : <Badge>중지</Badge>}</td>
              <td>
                <button type="button" className="text-xs underline" onClick={() => edit(s)}>
                  수정
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </Table>
      {mappings ? (
        <Section title="자동 매칭 기억 (거래처 상품코드 → SKU)" actions={<button type="button" className={buttonClass} onClick={() => setMappings(null)}>닫기</button>}>
          <Table>
            <thead>
              <tr>
                <th>거래처 상품코드</th>
                <th>거래처 상품명</th>
                <th>상품</th>
                <th>옵션</th>
                <th className="text-right">최근 단가</th>
                <th>최근 매입일</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {mappings.rows.map((m) => (
                <tr key={m.id}>
                  <td>{m.supplierProductCode ?? "-"}</td>
                  <td>{m.supplierProductName ?? "-"}</td>
                  <td>
                    <Link className="underline" href={`/admin/products/${m.productId}/edit`}>
                      상품 #{m.productId}
                    </Link>
                  </td>
                  <td>{[m.color, m.size].filter(Boolean).join(" / ") || "-"}</td>
                  <td className="text-right tabular-nums">{won(m.lastUnitCost)}</td>
                  <td>{m.lastPurchaseDate ?? "-"}</td>
                  <td>
                    <button type="button" className="text-xs text-danger underline" onClick={() => void deleteMapping(mappings.supplierId, m.id)}>
                      삭제
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Section>
      ) : null}
    </>
  );
}
