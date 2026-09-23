import Link from "next/link";
import { BrandLogo } from "@/components/brand/BrandLogo";
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
            <p className="heading-en text-xs tracking-[0.18em] text-muted-foreground">
              DAILY LOOK, A BETTER YOU
            </p>
            <h1 className="heading-ko text-3xl leading-tight text-foreground md:text-[2.75rem]">
              오늘, 더 아름답게
            </h1>
            <p className="max-w-md text-base leading-relaxed text-muted-foreground">
              포근한 감성과 여성스러운 실루엣을 담은 데일리 셀렉션
            </p>
            <div className="flex flex-wrap gap-3">
              <Link
                href="/products?sort=NEWEST"
                className="inline-flex h-11 items-center justify-center rounded-md bg-brand px-5 text-sm font-medium tracking-[0.02em] text-white transition-colors hover:bg-brand-hover"
              >
                NEW COLLECTION →
              </Link>
              <Link
                href="/style"
                className="inline-flex h-11 items-center justify-center rounded-md border border-border bg-surface px-5 text-sm font-medium hover:bg-surface-soft"
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
          <h2 className="heading-ko text-xl text-foreground">상황별 쇼핑</h2>
          <div className="flex flex-wrap gap-2">
            {occasions.map((item) => (
              <Link
                key={item}
                href={`/products?keyword=${encodeURIComponent(item)}`}
                className="inline-flex h-11 items-center rounded-md border border-border bg-surface px-4 text-sm hover:bg-surface-soft"
              >
                {item}
              </Link>
            ))}
          </div>
        </section>

        <section className="flex flex-col gap-4">
          <div className="flex items-end justify-between gap-3">
            <h2 className="heading-ko text-xl text-foreground">신상품</h2>
            <Link href="/products?sort=NEWEST" className="text-sm text-brand hover:underline">
              더보기
            </Link>
          </div>
          <ProductGrid products={newProducts.slice(0, 8)} />
        </section>

        <section className="rounded-md bg-surface-soft px-5 py-8 md:px-8">
          <h2 className="heading-ko mb-2 text-xl text-foreground">AI 스타일리스트</h2>
          <p className="mb-4 max-w-xl text-sm leading-relaxed text-muted-foreground">
            상황과 스타일, 예산에 맞는 코디를 추천해 드려요. OpenAI Key가 없어도 규칙 기반으로 동작합니다.
          </p>
          <Link
            href="/style"
            className="inline-flex h-11 items-center rounded-md bg-brand px-5 text-sm font-medium text-white hover:bg-brand-hover"
          >
            추천 시작하기
          </Link>
        </section>

        <section className="flex flex-col gap-4">
          <h2 className="heading-ko text-xl text-foreground">주간 베스트</h2>
          <ProductGrid products={bestProducts.slice(0, 8)} />
        </section>

        <section className="flex flex-col items-center gap-4 py-6 text-center">
          <p className="heading-ko max-w-xl text-xl leading-relaxed text-foreground md:text-2xl">
            “당신의 모든 순간이, 더 아름다워지기를.”
          </p>
          <BrandLogo size="footer" href={null} />
        </section>
      </Container>
    </main>
  );
}
