import { Container } from "@/components/ui/Container";
import { OrdersClient } from "@/components/orders/OrdersClient";

export default function MyOrdersPage() {
  return (
    <main className="py-8">
      <Container className="flex flex-col gap-6">
        <h1 className="text-2xl font-semibold">주문내역</h1>
        <OrdersClient />
      </Container>
    </main>
  );
}
