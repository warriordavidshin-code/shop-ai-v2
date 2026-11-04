import { Container } from "@/components/ui/Container";
import { CartClient } from "@/components/cart/CartClient";
import { RecoSection } from "@/components/recommendations/RecoSection";

export default function CartPage() {
  return (
    <main className="py-8">
      <Container className="flex flex-col gap-6">
        <h1 className="text-2xl font-semibold">장바구니</h1>
        <CartClient />
        <RecoSection path="cart" className="mt-8" />
      </Container>
    </main>
  );
}
