import { Container } from "@/components/ui/Container";
import { StyleClient } from "@/components/ai/StyleClient";

export default function StylePage() {
  return (
    <main className="py-8">
      <Container className="flex max-w-3xl flex-col gap-6">
        <h1 className="text-2xl font-semibold">AI 스타일리스트</h1>
        <p className="text-sm text-muted-foreground">
          OpenAI Key가 없어도 규칙 기반으로 추천합니다. 재고가 있는 판매 중 상품만 사용합니다.
        </p>
        <StyleClient />
      </Container>
    </main>
  );
}
