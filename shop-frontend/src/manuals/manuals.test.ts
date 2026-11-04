import { afterEach, describe, expect, it, vi } from "vitest";
import { contentDisposition, findManual, manuals, requiresAdmin, resolveViewerRole } from "./access";
import { escapeHtml, renderManual } from "./render";

describe("manuals", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("finds only known manuals and gates the operator manual", () => {
    expect(findManual("user")?.slug).toBe("user");
    expect(findManual("operator")?.slug).toBe("operator");
    expect(findManual("../etc/passwd")).toBeNull();
    expect(requiresAdmin(manuals.operator)).toBe(true);
    expect(requiresAdmin(manuals.user)).toBe(false);
  });

  it("renders a full document with a table of contents and unique anchors", () => {
    for (const manual of Object.values(manuals)) {
      const ids = manual.sections.map((section) => section.id);
      expect(new Set(ids).size).toBe(ids.length);
      const html = renderManual(manual, { downloadHref: `/manuals/${manual.slug}?download=1` });
      expect(html).toContain("<!doctype html>");
      expect(html).toContain(`<title>${manual.title} | Boutique Camel</title>`);
      for (const id of ids) {
        expect(html).toContain(`href="#${id}"`);
        expect(html).toContain(`<section id="${id}">`);
      }
      expect(html).toContain(`/manuals/${manual.slug}?download=1`);
      expect(html).not.toContain("${");
    }
  });

  it("keeps the core operating rules in the operator manual", () => {
    const html = renderManual(manuals.operator, { downloadHref: "#" });
    expect(html).toContain("입고 확정");
    expect(html).toContain("자동 발주는 없습니다");
    expect(html).toContain("AI_MOCK_ENABLED");
    expect(html).toContain("AI_PAID_FALLBACK_ENABLED");
    expect(html).toContain("유료 AI는 관리자 승인 없이 호출되지 않습니다");
    expect(html).toContain("AI 추천관리");
    expect(html).not.toMatch(/sk-[A-Za-z0-9]{10,}/);
    expect(html).not.toMatch(/AIza[0-9A-Za-z_-]{20,}/);
  });

  it("escapes text and builds RFC 5987 download headers", () => {
    expect(escapeHtml(`<a href="x">'&'</a>`)).toBe("&lt;a href=&quot;x&quot;&gt;&#39;&amp;&#39;&lt;/a&gt;");
    expect(contentDisposition("manual.html", true)).toBe(
      "attachment; filename=\"manual.html\"; filename*=UTF-8''manual.html",
    );
    expect(contentDisposition("manual.html", false)).toMatch(/^inline;/);
  });

  it("resolves the viewer role from the backend session", async () => {
    expect(await resolveViewerRole(null, "http://backend")).toBe("ANONYMOUS");

    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ role: "ADMIN" }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    expect(await resolveViewerRole("SESSION=a", "http://backend")).toBe("ADMIN");
    expect(fetchMock).toHaveBeenCalledWith(
      "http://backend/api/members/me",
      expect.objectContaining({ headers: { cookie: "SESSION=a" } }),
    );

    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ role: "CUSTOMER" }), { status: 200 }));
    expect(await resolveViewerRole("SESSION=b", "http://backend")).toBe("CUSTOMER");

    fetchMock.mockResolvedValueOnce(new Response("", { status: 401 }));
    expect(await resolveViewerRole("SESSION=c", "http://backend")).toBe("ANONYMOUS");

    fetchMock.mockRejectedValueOnce(new Error("down"));
    expect(await resolveViewerRole("SESSION=d", "http://backend")).toBe("ANONYMOUS");
  });
});
