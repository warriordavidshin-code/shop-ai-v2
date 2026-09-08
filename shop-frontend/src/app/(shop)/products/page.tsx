import Link from "next/link";
import { Container } from "@/components/ui/Container";
import { ProductGrid } from "@/components/product/ProductCard";
import { fetchProducts } from "@/features/products/api";
import { ProductFilters } from "@/components/product/ProductFilters";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

function first(value: string | string[] | undefined): string | undefined {
  if (Array.isArray(value)) return value[0];
  return value;
}

export default async function ProductsPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams;
  const query = {
    page: first(params.page) ?? "0",
    category: first(params.category),
    keyword: first(params.keyword),
    sort: first(params.sort) ?? "RECOMMENDED",
    minPrice: first(params.minPrice),
    maxPrice: first(params.maxPrice),
    color: first(params.color),
    size: first(params.size),
    availableOnly: first(params.availableOnly),
  };

  let page;
  try {
    page = await fetchProducts(query);
  } catch {
    page = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
  }

  return (
    <main className="py-8">
      <Container className="flex flex-col gap-6">
        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-semibold text-foreground">상품</h1>
          <p className="text-sm text-muted-foreground">총 {page.totalElements}개</p>
        </div>
        <ProductFilters current={query} />
        <ProductGrid products={page.content} />
        {page.totalPages > 1 ? (
          <div className="flex flex-wrap gap-2">
            {Array.from({ length: page.totalPages }, (_, i) => {
              const qs = new URLSearchParams();
              Object.entries({ ...query, page: String(i) }).forEach(([k, v]) => {
                if (v) qs.set(k, v);
              });
              return (
                <Link
                  key={i}
                  href={`/products?${qs}`}
                  className={`inline-flex h-11 min-w-11 items-center justify-center rounded-xl border px-3 text-sm ${
                    i === page.page
                      ? "border-brand bg-brand-soft text-foreground"
                      : "border-border bg-surface text-muted-foreground hover:bg-surface-soft"
                  }`}
                >
                  {i + 1}
                </Link>
              );
            })}
          </div>
        ) : null}
      </Container>
    </main>
  );
}
