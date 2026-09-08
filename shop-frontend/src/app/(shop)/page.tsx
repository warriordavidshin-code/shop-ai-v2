import Link from "next/link";
import { Container } from "@/components/ui/Container";
import { ProductGrid } from "@/components/product/ProductCard";
import { HomeHeroBanner } from "@/components/home/HomeHeroBanner";
import { fetchBestProducts, fetchNewProducts } from "@/features/products/api";

const occasions = ["출근", "데이트", "결혼식", "여행", "데일리", "키즈"];

async function fetchHeroBanners() {
  const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";
  try {
    const response = await fetch(`${backendUrl}/api/hero-banners`, { cache: "no-store" });
    if (!response.ok) return [];
    return response.json();
  } catch {
    return [];
  }
}

export default async function HomePage() {
  let newProducts = [] as Awaited<ReturnType<typeof fetchNewProducts>>;
  let bestProducts = [] as Awaited<ReturnType<typeof fetchBestProducts>>;
  let banners = [] as Array<{
    bannerId: number;
    imageUrl: string;
    overlayText: string | null;
  }>;
  try {
    [newProducts, bestProducts, banners] = await Promise.all([
      fetchNewProducts(),
      fetchBestProducts(),
      fetchHeroBanners(),
    ]);
  } catch {
    newProducts = [];
    bestProducts = [];
  }

  return (
    <main>
      <section className="border-b border-border bg-background">
        <Container className="grid gap-8 py-10 lg:grid-cols-2 lg:items-center lg:py-16">
          <div className="flex flex-col gap-5">
            <p className="text-sm tracking-wide text-muted-foreground">BoutiqueCamel</p>
            <h1 className="text-3xl font-semibold leading-tight text-foreground md:text-4xl">
              오늘의 나에게 어울리는 옷
            </h1>
            <p className="max-w-md text-base text-muted-foreground">
              편안한 데일리룩부터 특별한 날의 코디까지, 쁘띠카멜이 함께 골라드려요.
            </p>
            <div className="flex flex-wrap gap-3">
              <Link
                href="/products?sort=NEWEST"
                className="inline-flex h-11 items-center justify-center rounded-xl bg-brand px-5 text-sm font-medium text-white hover:bg-brand-hover"
              >
                신상품 보기
              </Link>
              <Link
                href="/style"
                className="inline-flex h-11 items-center justify-center rounded-xl border border-border bg-surface px-5 text-sm font-medium hover:bg-surface-soft"
              >
                AI 추천받기
              </Link>
            </div>
          </div>
          <HomeHeroBanner banners={Array.isArray(banners) ? banners : []} />
        </Container>
      </section>

      <Container className="flex flex-col gap-14 py-12">
        <section className="flex flex-col gap-4">
          <h2 className="text-xl font-semibold">상황별 쇼핑</h2>
          <div className="flex flex-wrap gap-2">
            {occasions.map((item) => (
              <Link
                key={item}
                href={`/products?keyword=${encodeURIComponent(item)}`}
                className="inline-flex h-11 items-center rounded-xl border border-border bg-surface px-4 text-sm hover:bg-surface-soft"
              >
                {item}
              </Link>
            ))}
          </div>
        </section>

        <section className="flex flex-col gap-4">
          <div className="flex items-end justify-between gap-3">
            <h2 className="text-xl font-semibold">신상품</h2>
            <Link href="/products?sort=NEWEST" className="text-sm text-brand hover:underline">
              더보기
            </Link>
          </div>
          <ProductGrid products={newProducts.slice(0, 8)} />
        </section>

        <section className="rounded-xl bg-surface-soft px-5 py-8 md:px-8">
          <h2 className="mb-2 text-xl font-semibold">AI 스타일리스트</h2>
          <p className="mb-4 max-w-xl text-sm text-muted-foreground">
            상황과 스타일, 예산에 맞는 코디를 추천해 드려요. OpenAI Key가 없어도 규칙 기반으로 동작합니다.
          </p>
          <Link
            href="/style"
            className="inline-flex h-11 items-center rounded-xl bg-brand px-5 text-sm font-medium text-white hover:bg-brand-hover"
          >
            추천 시작하기
          </Link>
        </section>

        <section className="flex flex-col gap-4">
          <h2 className="text-xl font-semibold">주간 베스트</h2>
          <ProductGrid products={bestProducts.slice(0, 8)} />
        </section>

        <section className="flex flex-col gap-3">
          <h2 className="text-xl font-semibold">브랜드 이야기</h2>
          <p className="max-w-2xl text-sm leading-relaxed text-muted-foreground">
            화이트와 크림 베이지의 포근한 분위기 속에서, 여성과 아이를 위한 편안한 데일리룩을 만듭니다.
          </p>
        </section>
      </Container>
    </main>
  );
}
