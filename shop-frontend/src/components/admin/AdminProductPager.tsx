import Link from "next/link";

export function AdminProductPager({
  basePath,
  page,
  totalPages,
}: {
  basePath: string;
  page: number;
  totalPages: number;
}) {
  if (totalPages <= 1) return null;
  const linkClass = "rounded-lg border border-border bg-surface px-3 py-1.5 hover:bg-surface-soft";
  return (
    <nav className="flex items-center justify-center gap-3 text-sm" aria-label="페이지">
      {page > 0 ? (
        <Link href={`${basePath}?page=${page - 1}`} className={linkClass}>
          이전
        </Link>
      ) : null}
      <span className="text-muted-foreground">
        {page + 1} / {totalPages}
      </span>
      {page + 1 < totalPages ? (
        <Link href={`${basePath}?page=${page + 1}`} className={linkClass}>
          다음
        </Link>
      ) : null}
    </nav>
  );
}
