import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import Link from "next/link";
import { Container } from "@/components/ui/Container";
import { z } from "zod";

async function fetchAdminProducts() {
  const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";
  const cookieStore = await cookies();
  const cookieHeader = cookieStore
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join("; ");
  const response = await fetch(`${backendUrl}/api/admin/products`, {
    headers: cookieHeader ? { cookie: cookieHeader } : {},
    cache: "no-store",
  });
  if (response.status === 401 || response.status === 403) {
    return null;
  }
  if (!response.ok) {
    return [];
  }
  const data = await response.json();
  const parsed = z
    .object({
      content: z.array(
        z.object({
          productId: z.number(),
          productName: z.string(),
          brandName: z.string().optional(),
          status: z.string(),
          salePrice: z.coerce.number().optional(),
        }),
      ),
    })
    .parse(data);
  return parsed.content;
}

export default async function AdminProductsPage() {
  const products = await fetchAdminProducts();
  if (products === null) {
    redirect("/login?next=/admin/products");
  }

  return (
    <main className="py-10">
      <Container className="flex flex-col gap-6">
        <div className="flex items-center justify-between gap-3">
          <h1 className="text-2xl font-semibold">상품 관리</h1>
          <Link
            href="/admin/products/new"
            className="inline-flex h-11 items-center rounded-xl bg-brand px-4 text-sm font-medium text-white"
          >
            상품 등록
          </Link>
        </div>
        <div className="overflow-x-auto rounded-xl border border-border bg-surface">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-surface-soft text-muted-foreground">
              <tr>
                <th className="px-4 py-3">ID</th>
                <th className="px-4 py-3">상품명</th>
                <th className="px-4 py-3">상태</th>
                <th className="px-4 py-3">편집</th>
              </tr>
            </thead>
            <tbody>
              {products.map((product) => (
                <tr key={product.productId} className="border-t border-border">
                  <td className="px-4 py-3">{product.productId}</td>
                  <td className="px-4 py-3">{product.productName}</td>
                  <td className="px-4 py-3">{product.status}</td>
                  <td className="px-4 py-3">
                    <Link
                      href={`/admin/products/${product.productId}/edit`}
                      className="text-brand hover:underline"
                    >
                      수정
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Container>
    </main>
  );
}
