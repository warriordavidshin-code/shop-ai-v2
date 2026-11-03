"use client";

import Link from "next/link";
import type { ReactNode } from "react";

export const inputClass = "h-10 rounded-lg border border-border bg-surface px-3 text-sm";
export const buttonClass =
  "inline-flex h-10 items-center justify-center rounded-lg border border-border bg-surface px-3 text-sm hover:bg-surface-soft disabled:opacity-50";
export const primaryButtonClass =
  "inline-flex h-10 items-center justify-center rounded-lg bg-brand px-4 text-sm text-white hover:opacity-90 disabled:opacity-50";
export const dangerButtonClass =
  "inline-flex h-10 items-center justify-center rounded-lg border border-danger px-3 text-sm text-danger hover:bg-danger/5 disabled:opacity-50";

export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="flex flex-col gap-1">
        <h1 className="heading-ko text-2xl">{title}</h1>
        {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
    </div>
  );
}

export function StatCard({
  label,
  value,
  sub,
  tone,
  href,
}: {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  tone?: "warn" | "danger" | "ok";
  href?: string;
}) {
  const toneClass =
    tone === "danger" ? "text-danger" : tone === "warn" ? "text-amber-600" : tone === "ok" ? "text-emerald-700" : "";
  const body = (
    <>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className={`mt-1 text-xl font-semibold tabular-nums ${toneClass}`}>{value}</p>
      {sub ? <p className="mt-1 text-xs text-muted-foreground">{sub}</p> : null}
    </>
  );
  const cls = "rounded-xl border border-border bg-surface p-4";
  return href ? (
    <Link href={href} className={`${cls} transition-colors hover:bg-surface-soft`}>
      {body}
    </Link>
  ) : (
    <div className={cls}>{body}</div>
  );
}

export function Notice({ tone = "info", children }: { tone?: "info" | "warn" | "danger" | "ok"; children: ReactNode }) {
  const cls = {
    info: "border-border bg-surface-soft text-foreground",
    warn: "border-amber-300 bg-amber-50 text-amber-900",
    danger: "border-danger/40 bg-danger/5 text-danger",
    ok: "border-emerald-300 bg-emerald-50 text-emerald-900",
  }[tone];
  return <div className={`rounded-lg border px-3 py-2 text-sm ${cls}`}>{children}</div>;
}

export function WarningList({ items, tone = "warn" }: { items: string[] | null | undefined; tone?: "warn" | "info" }) {
  if (!items || items.length === 0) return null;
  return (
    <Notice tone={tone}>
      <ul className="list-disc space-y-0.5 pl-4">
        {items.map((item, i) => (
          <li key={i}>{item}</li>
        ))}
      </ul>
    </Notice>
  );
}

export function Table({ children }: { children: ReactNode }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-border">
      <table className="w-full min-w-max text-sm [&_td]:border-t [&_td]:border-border [&_td]:px-3 [&_td]:py-2 [&_th]:bg-surface-soft [&_th]:px-3 [&_th]:py-2 [&_th]:text-left [&_th]:font-medium [&_th]:text-muted-foreground">
        {children}
      </table>
    </div>
  );
}

export function Pager({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-center gap-2 text-sm">
      <button type="button" className={buttonClass} disabled={page <= 0} onClick={() => onPage(page - 1)}>
        이전
      </button>
      <span className="tabular-nums text-muted-foreground">
        {page + 1} / {totalPages}
      </span>
      <button type="button" className={buttonClass} disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
        다음
      </button>
    </div>
  );
}

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: ReactNode }) {
  return (
    <label className="flex flex-col gap-1 text-sm">
      <span className="font-medium">{label}</span>
      {children}
      {hint ? <span className="text-xs text-muted-foreground">{hint}</span> : null}
    </label>
  );
}

export function Badge({ children, tone }: { children: ReactNode; tone?: "warn" | "ok" | "danger" | "muted" }) {
  const cls = {
    warn: "bg-amber-100 text-amber-900",
    ok: "bg-emerald-100 text-emerald-900",
    danger: "bg-danger/10 text-danger",
    muted: "bg-surface-soft text-muted-foreground",
  }[tone ?? "muted"];
  return <span className={`inline-flex rounded px-1.5 py-0.5 text-xs ${cls}`}>{children}</span>;
}

// ---------- SVG charts (no chart dependency) ----------

const PALETTE = ["#8a6a4f", "#c49a6c", "#5b7a8c", "#9cb08a", "#d4a5a5", "#7d6b91", "#e0c07a", "#6f8f8f", "#b07d62", "#999"];

function compact(n: number): string {
  const abs = Math.abs(n);
  if (abs >= 100_000_000) return `${(n / 100_000_000).toFixed(1)}억`;
  if (abs >= 10_000) return `${Math.round(n / 10_000).toLocaleString("ko-KR")}만`;
  return Math.round(n).toLocaleString("ko-KR");
}

export type Series = { name: string; values: number[]; color?: string };

/** Grouped vertical bars; optional line series drawn on top with the same scale. */
export function BarChart({
  labels,
  series,
  lines = [],
  height = 220,
  emptyText = "데이터가 없습니다.",
}: {
  labels: string[];
  series: Series[];
  lines?: Series[];
  height?: number;
  emptyText?: string;
}) {
  const all = [...series, ...lines].flatMap((s) => s.values);
  if (labels.length === 0 || all.every((v) => !v)) {
    return <p className="py-10 text-center text-sm text-muted-foreground">{emptyText}</p>;
  }
  const max = Math.max(1, ...all);
  const min = Math.min(0, ...all);
  const range = max - min;
  const width = Math.max(320, labels.length * 56);
  const pad = { l: 48, r: 8, t: 10, b: 26 };
  const innerH = height - pad.t - pad.b;
  const innerW = width - pad.l - pad.r;
  const slot = innerW / labels.length;
  const barW = Math.max(4, (slot * 0.7) / Math.max(1, series.length));
  const y = (v: number) => pad.t + ((max - v) / range) * innerH;
  const ticks = [max, max - range / 2, min];
  return (
    <div className="flex flex-col gap-2">
      <div className="overflow-x-auto">
        <svg width={width} height={height} role="img" className="text-muted-foreground">
          {ticks.map((t, i) => (
            <g key={i}>
              <line x1={pad.l} x2={width - pad.r} y1={y(t)} y2={y(t)} stroke="currentColor" strokeOpacity={0.15} />
              <text x={pad.l - 4} y={y(t) + 4} fontSize={10} textAnchor="end" fill="currentColor">
                {compact(t)}
              </text>
            </g>
          ))}
          {labels.map((label, i) => (
            <g key={label + i}>
              {series.map((s, j) => {
                const v = s.values[i] ?? 0;
                const x = pad.l + slot * i + (slot - barW * series.length) / 2 + barW * j;
                return (
                  <rect
                    key={s.name}
                    x={x}
                    y={Math.min(y(v), y(0))}
                    width={barW}
                    height={Math.abs(y(v) - y(0))}
                    fill={s.color ?? PALETTE[j % PALETTE.length]}
                  >
                    <title>{`${label} ${s.name}: ${v.toLocaleString("ko-KR")}`}</title>
                  </rect>
                );
              })}
              <text x={pad.l + slot * i + slot / 2} y={height - 8} fontSize={10} textAnchor="middle" fill="currentColor">
                {label}
              </text>
            </g>
          ))}
          {lines.map((s, j) => (
            <polyline
              key={s.name}
              fill="none"
              strokeWidth={2}
              stroke={s.color ?? PALETTE[(series.length + j) % PALETTE.length]}
              points={s.values.map((v, i) => `${pad.l + slot * i + slot / 2},${y(v ?? 0)}`).join(" ")}
            />
          ))}
        </svg>
      </div>
      <Legend items={[...series, ...lines].map((s, i) => ({ name: s.name, color: s.color ?? PALETTE[i % PALETTE.length] }))} />
    </div>
  );
}

export function HBarChart({ items, emptyText = "데이터가 없습니다.", format = compact }: {
  items: { name: string; value: number }[];
  emptyText?: string;
  format?: (n: number) => string;
}) {
  if (items.length === 0 || items.every((i) => !i.value)) {
    return <p className="py-10 text-center text-sm text-muted-foreground">{emptyText}</p>;
  }
  const max = Math.max(1, ...items.map((i) => Math.abs(i.value)));
  return (
    <ul className="flex flex-col gap-1.5">
      {items.map((item, i) => (
        <li key={item.name + i} className="grid grid-cols-[8rem_1fr_5rem] items-center gap-2 text-xs">
          <span className="truncate" title={item.name}>
            {item.name}
          </span>
          <span className="h-3 rounded bg-surface-soft">
            <span
              className="block h-3 rounded"
              style={{
                width: `${(Math.abs(item.value) / max) * 100}%`,
                background: item.value < 0 ? "#c0392b" : PALETTE[0],
              }}
            />
          </span>
          <span className="text-right tabular-nums">{format(item.value)}</span>
        </li>
      ))}
    </ul>
  );
}

export function DonutChart({ items, emptyText = "데이터가 없습니다." }: { items: { name: string; value: number }[]; emptyText?: string }) {
  const positive = items.filter((i) => i.value > 0);
  const total = positive.reduce((a, b) => a + b.value, 0);
  if (total <= 0) {
    return <p className="py-10 text-center text-sm text-muted-foreground">{emptyText}</p>;
  }
  const r = 60;
  const c = 2 * Math.PI * r;
  let offset = 0;
  return (
    <div className="flex flex-wrap items-center gap-4">
      <svg width={160} height={160} viewBox="0 0 160 160" role="img">
        <g transform="rotate(-90 80 80)">
          {positive.map((item, i) => {
            const len = (item.value / total) * c;
            const el = (
              <circle
                key={item.name + i}
                cx={80}
                cy={80}
                r={r}
                fill="none"
                stroke={PALETTE[i % PALETTE.length]}
                strokeWidth={28}
                strokeDasharray={`${len} ${c - len}`}
                strokeDashoffset={-offset}
              >
                <title>{`${item.name}: ${((item.value / total) * 100).toFixed(1)}%`}</title>
              </circle>
            );
            offset += len;
            return el;
          })}
        </g>
      </svg>
      <ul className="flex flex-col gap-1 text-xs">
        {positive.map((item, i) => (
          <li key={item.name + i} className="flex items-center gap-2">
            <span className="inline-block h-2.5 w-2.5 rounded-sm" style={{ background: PALETTE[i % PALETTE.length] }} />
            <span>{item.name}</span>
            <span className="tabular-nums text-muted-foreground">{((item.value / total) * 100).toFixed(1)}%</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function Legend({ items }: { items: { name: string; color: string }[] }) {
  return (
    <div className="flex flex-wrap gap-3 text-xs text-muted-foreground">
      {items.map((item) => (
        <span key={item.name} className="inline-flex items-center gap-1">
          <span className="inline-block h-2.5 w-2.5 rounded-sm" style={{ background: item.color }} />
          {item.name}
        </span>
      ))}
    </div>
  );
}

export function Section({ title, children, actions }: { title: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold">{title}</h2>
        {actions}
      </div>
      {children}
    </section>
  );
}
