import { redirect } from "next/navigation";

type SearchParams = Promise<{ q?: string }>;

export default async function SearchPage({ searchParams }: { searchParams: SearchParams }) {
  const { q } = await searchParams;
  const qs = new URLSearchParams();
  if (q) qs.set("keyword", q);
  redirect(`/products?${qs}`);
}
