export type ManualSection = {
  id: string;
  title: string;
  /** Trusted, hand-written HTML. */
  html: string;
};

export type Manual = {
  slug: "operator" | "user";
  title: string;
  subtitle: string;
  audience: string;
  updatedAt: string;
  downloadFileName: string;
  sections: ManualSection[];
};

export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

const STYLE = `
  :root { --brand:#8a6a4f; --ink:#2b2622; --muted:#6f665e; --line:#e6ded5; --soft:#f8f4ef; --warn:#fff6dd; --warn-line:#e8cf86; }
  * { box-sizing:border-box; }
  body { margin:0; font-family:"Pretendard","Apple SD Gothic Neo","Malgun Gothic","Noto Sans KR",sans-serif; color:var(--ink); line-height:1.7; background:#fff; }
  .wrap { max-width:960px; margin:0 auto; padding:32px 24px 80px; }
  header.cover { border-bottom:2px solid var(--brand); padding-bottom:20px; margin-bottom:28px; }
  header.cover .brand { letter-spacing:.18em; font-size:12px; color:var(--brand); font-weight:700; }
  header.cover h1 { margin:8px 0 4px; font-size:30px; }
  header.cover p { margin:2px 0; color:var(--muted); }
  .toolbar { display:flex; gap:8px; flex-wrap:wrap; margin-top:16px; }
  .toolbar a, .toolbar button { font:inherit; font-size:14px; border:1px solid var(--brand); color:var(--brand); background:#fff; border-radius:8px; padding:6px 14px; cursor:pointer; text-decoration:none; }
  .toolbar .primary { background:var(--brand); color:#fff; }
  nav.toc { background:var(--soft); border:1px solid var(--line); border-radius:12px; padding:16px 24px; margin-bottom:32px; }
  nav.toc h2 { margin:0 0 8px; font-size:16px; }
  nav.toc ol { margin:0; padding-left:20px; columns:2; column-gap:32px; }
  nav.toc a { color:var(--ink); text-decoration:none; }
  nav.toc a:hover { color:var(--brand); text-decoration:underline; }
  section { margin-bottom:40px; }
  section > h2 { font-size:22px; border-left:5px solid var(--brand); padding-left:12px; margin:0 0 14px; }
  h3 { font-size:17px; margin:22px 0 8px; }
  table { width:100%; border-collapse:collapse; margin:10px 0 16px; font-size:14px; }
  th, td { border:1px solid var(--line); padding:7px 10px; text-align:left; vertical-align:top; }
  th { background:var(--soft); white-space:nowrap; }
  code { background:var(--soft); border:1px solid var(--line); border-radius:4px; padding:0 4px; font-size:13px; }
  pre { background:#2b2622; color:#f8f4ef; border-radius:8px; padding:12px 16px; overflow-x:auto; font-size:13px; }
  .note, .warn { border-radius:10px; padding:10px 14px; margin:12px 0; font-size:14px; }
  .note { background:var(--soft); border:1px solid var(--line); }
  .warn { background:var(--warn); border:1px solid var(--warn-line); }
  ol.steps > li { margin-bottom:6px; }
  .kbd { display:inline-block; border:1px solid var(--line); border-bottom-width:2px; border-radius:6px; padding:0 6px; font-size:13px; background:#fff; }
  footer.end { border-top:1px solid var(--line); margin-top:48px; padding-top:12px; font-size:12px; color:var(--muted); }
  @media (max-width:640px) { nav.toc ol { columns:1; } header.cover h1 { font-size:24px; } }
  @media print {
    .toolbar { display:none; }
    .wrap { max-width:none; padding:0; }
    section { break-inside:auto; }
    section > h2 { break-after:avoid; }
    table, .note, .warn, pre { break-inside:avoid; }
    a { color:inherit; text-decoration:none; }
  }
`;

export function renderManual(manual: Manual, options: { downloadHref: string }): string {
  const toc = manual.sections
    .map((section) => `<li><a href="#${section.id}">${escapeHtml(section.title)}</a></li>`)
    .join("");
  const body = manual.sections
    .map(
      (section, index) =>
        `<section id="${section.id}"><h2>${index + 1}. ${escapeHtml(section.title)}</h2>${section.html}</section>`,
    )
    .join("\n");

  return `<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>${escapeHtml(manual.title)} | Boutique Camel</title>
<style>${STYLE}</style>
</head>
<body>
<div class="wrap">
<header class="cover">
  <div class="brand">BOUTIQUE CAMEL</div>
  <h1>${escapeHtml(manual.title)}</h1>
  <p>${escapeHtml(manual.subtitle)}</p>
  <p>대상: ${escapeHtml(manual.audience)} · 기준일: ${escapeHtml(manual.updatedAt)}</p>
  <div class="toolbar">
    <a class="primary" href="${escapeHtml(options.downloadHref)}">파일로 다운로드</a>
    <button type="button" onclick="window.print()">인쇄 / PDF로 저장</button>
  </div>
</header>
<nav class="toc" aria-label="목차"><h2>목차</h2><ol>${toc}</ol></nav>
${body}
<footer class="end">${escapeHtml(manual.title)} · Boutique Camel · ${escapeHtml(manual.updatedAt)} 기준. 화면 문구는 업데이트에 따라 조금 달라질 수 있습니다.</footer>
</div>
</body>
</html>
`;
}
