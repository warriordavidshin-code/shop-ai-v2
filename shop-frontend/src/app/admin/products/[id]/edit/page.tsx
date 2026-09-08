import { cookies } from "next/headers";
import { notFound, redirect } from "next/navigation";
import { Container } from "@/components/ui/Container";
import { AdminProductForm } from "@/components/admin/AdminProductForm";

type Params = Promise<{ id: string }>;

async function fetchAdminProduct(id: string) {
  const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";
  const cookieStore = await cookies();
  const cookieHeader = cookieStore
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join("; ");
  const response = await fetch(`${backendUrl}/api/admin/products/${id}`, {
    headers: cookieHeader ? { cookie: cookieHeader } : {},
    cache: "no-store",
  });
  if (response.status === 401 || response.status === 403) return "forbidden" as const;
  if (response.status === 404) return null;
  if (!response.ok) {
    // fallback: public detail for form seed
    const pub = await fetch(`${backendUrl}/api/products/${id}`, { cache: "no-store" });
    if (!pub.ok) return null;
    return pub.json();
  }
  return response.json();
}

export default async function AdminEditProductPage({ params }: { params: Params }) {
  const { id } = await params;
  const data = await fetchAdminProduct(id);
  if (data === "forbidden") redirect("/login?next=/admin/products");
  if (!data) notFound();

  return (
    <main className="py-10">
      <Container>
        <h1 className="mb-6 text-2xl font-semibold">상품 수정</h1>
        <AdminProductForm
          mode="edit"
          productId={Number(id)}
          initial={{
            productName: data.productName ?? "",
            brandName: data.brandName ?? "BoutiqueCamel",
            categoryId: data.categoryId ?? 3,
            summary: data.summary ?? "",
            description: data.description ?? "",
            normalPrice: Number(data.normalPrice ?? 0),
            salePrice: Number(data.salePrice ?? 0),
            status: data.status ?? "ON_SALE",
            images: Array.isArray(data.images)
              ? data.images.map(
                  (
                    img: {
                      imageUrl?: string;
                      imageType?: string;
                      altText?: string;
                      sortOrder?: number;
                    },
                    index: number,
                  ) => ({
                    imageUrl: img.imageUrl ?? "",
                    imageType: img.imageType === "MAIN" ? "MAIN" : "DETAIL",
                    altText: img.altText ?? "",
                    sortOrder: img.sortOrder ?? index,
                  }),
                )
              : [],
          }}
        />
      </Container>
    </main>
  );
}
