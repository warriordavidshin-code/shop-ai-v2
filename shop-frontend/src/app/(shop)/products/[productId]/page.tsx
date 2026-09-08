import { notFound } from "next/navigation";
import { Container } from "@/components/ui/Container";
import { ProductDetailClient } from "@/components/product/ProductDetailClient";
import { fetchProduct } from "@/features/products/api";

type Params = Promise<{ productId: string }>;

export default async function ProductDetailPage({ params }: { params: Params }) {
  const { productId } = await params;
  const product = await fetchProduct(productId);
  if (!product) {
    notFound();
  }

  return (
    <main className="py-8">
      <Container>
        <ProductDetailClient product={product} />
      </Container>
    </main>
  );
}
