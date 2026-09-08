import Link from "next/link";
import { Price } from "@/components/ui/Price";
import { ImageRollingGallery } from "@/components/ui/ImageRollingGallery";
import type { ProductSummary } from "@/features/products/api";

export function ProductCard({ product }: { product: ProductSummary }) {
  const urls =
    product.imageUrls && product.imageUrls.length > 0
      ? product.imageUrls
      : product.mainImageUrl
        ? [product.mainImageUrl]
        : [];

  return (
    <article className="group flex flex-col gap-3">
      <Link href={`/products/${product.productId}`} className="block">
        <ImageRollingGallery
          images={urls.map((url) => ({ url, alt: product.productName }))}
          placeholderLabel={product.brandName}
          className="transition-transform duration-200 group-hover:-translate-y-0.5"
        />
      </Link>
      <div className="flex flex-col gap-1">
        <p className="text-xs text-muted-foreground">{product.brandName}</p>
        <Link
          href={`/products/${product.productId}`}
          className="line-clamp-2 text-sm font-medium text-foreground hover:underline"
        >
          {product.productName}
        </Link>
        <Price
          salePrice={product.salePrice}
          normalPrice={product.normalPrice}
          discountRate={product.discountRate}
        />
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <span>색상 {product.colorCount}</span>
          {product.soldOut ? (
            <span className="font-medium text-danger" aria-label="품절">
              품절
            </span>
          ) : null}
        </div>
      </div>
    </article>
  );
}

export function ProductGrid({ products }: { products: ProductSummary[] }) {
  if (products.length === 0) {
    return (
      <p className="rounded-xl border border-border bg-surface-soft px-4 py-10 text-center text-sm text-muted-foreground">
        조건에 맞는 상품이 없습니다.
      </p>
    );
  }

  return (
    <div className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 lg:grid-cols-4">
      {products.map((product) => (
        <ProductCard key={product.productId} product={product} />
      ))}
    </div>
  );
}
