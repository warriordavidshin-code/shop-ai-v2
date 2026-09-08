import { Container } from "@/components/ui/Container";
import { CheckoutClient } from "@/components/orders/CheckoutClient";

export default function CheckoutPage() {
  return (
    <main className="py-8">
      <Container className="flex flex-col gap-6">
        <h1 className="text-2xl font-semibold">주문서 · Mock 결제</h1>
        <CheckoutClient />
      </Container>
    </main>
  );
}
