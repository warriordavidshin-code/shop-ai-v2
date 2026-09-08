import { Container } from "@/components/ui/Container";

export default function AdminReviewsPage() {
  return (
    <main className="py-10">
      <Container className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">리뷰 관리</h1>
        <p className="text-sm text-muted-foreground">
          리뷰 숨김은 `PATCH /api/admin/reviews/{"{id}"}/status` API로 처리합니다.
        </p>
      </Container>
    </main>
  );
}
