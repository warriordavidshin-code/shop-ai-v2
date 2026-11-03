"use client";

import { useEffect, useState } from "react";
import { shopFetch } from "@/lib/api/client";
import type { Evidence } from "@/features/admin/ops";

export function evidencePath(id: number, download = false): string {
  return `/admin/purchases/evidence/${id}/file${download ? "?download=true" : ""}`;
}

/** Loads the private file through the authenticated proxy and shows it from a blob URL. */
export function EvidencePreview({ evidence, className }: { evidence: Evidence; className?: string }) {
  const [url, setUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let revoked = false;
    let objectUrl: string | null = null;
    setUrl(null);
    setFailed(false);
    shopFetch(evidencePath(evidence.id))
      .then(async (response) => {
        if (!response.ok) throw new Error(String(response.status));
        const blob = await response.blob();
        if (revoked) return;
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      })
      .catch(() => setFailed(true));
    return () => {
      revoked = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [evidence.id]);

  if (failed) {
    return <p className="p-6 text-center text-sm text-danger">원본 파일을 불러오지 못했습니다.</p>;
  }
  if (!url) {
    return <p className="p-6 text-center text-sm text-muted-foreground">원본 불러오는 중...</p>;
  }
  if (evidence.contentType === "application/pdf") {
    return <iframe src={url} title={evidence.originalFileName} className={className ?? "h-[70vh] w-full rounded-lg border border-border"} />;
  }
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img src={url} alt={evidence.originalFileName} className={className ?? "max-h-[70vh] w-full rounded-lg border border-border object-contain"} />
  );
}

export async function openEvidence(id: number, download: boolean) {
  const response = await shopFetch(evidencePath(id, download));
  if (!response.ok) throw new Error("파일을 열 수 없습니다.");
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  if (download) {
    const link = document.createElement("a");
    link.href = url;
    const header = response.headers.get("content-disposition") ?? "";
    const match = /filename\*=UTF-8''([^;]+)/i.exec(header);
    link.download = match ? decodeURIComponent(match[1]) : `evidence-${id}`;
    document.body.appendChild(link);
    link.click();
    link.remove();
  } else {
    window.open(url, "_blank", "noopener");
  }
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
