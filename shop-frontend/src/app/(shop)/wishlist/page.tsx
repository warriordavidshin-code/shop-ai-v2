import { Container } from "@/components/ui/Container";
import { WishlistClient } from "@/components/wishlist/WishlistClient";

export default function WishlistPage() {
  return (
    <main className="py-8">
      <Container className="flex flex-col gap-6">
        <h1 className="text-2xl font-semibold">찜</h1>
        <WishlistClient />
      </Container>
    </main>
  );
}
