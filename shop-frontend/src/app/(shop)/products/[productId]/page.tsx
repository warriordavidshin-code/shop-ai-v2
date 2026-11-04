import { notFound } from "next/navigation";
import { Container } from "@/components/ui/Container";
import { ProductDetailClient } from "@/components/product/ProductDetailClient";
import { RecoSection } from "@/components/recommendations/RecoSection";
import { ProductViewTracker } from "@/components/recommendations/Trackers";
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
      <Container className="flex flex-col gap-14">
        <ProductDetailClient product={product} />
        <ProductViewTracker productId={product.productId} />
        <RecoSection path={`similar/${product.productId}`} title="이 상품과 비슷한 상품" />
        <RecoSection path={`coordination/${product.productId}`} />
        <RecoSection path={`similar-price/${product.productId}`} />
      </Container>
    </main>
  );
}
