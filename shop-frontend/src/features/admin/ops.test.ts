import { describe, expect, it } from "vitest";
import { fileNameFromDisposition, pct, query, won } from "@/features/admin/ops";
import { textToHtml } from "@/components/admin/ops/AiProductPanel";
import { activeLink } from "@/components/admin/AdminNav";

describe("ops helpers", () => {
  it("decodes RFC 5987 Korean filenames from content-disposition", () => {
    const header =
      "attachment; filename=\"=?UTF-8?Q?x?=\"; filename*=UTF-8''%EC%83%81%ED%92%88%EB%A7%A4%EC%9E%85%EC%9E%A5%EB%B6%80_2026%EB%85%84_11%EC%9B%94.xlsx";
    expect(fileNameFromDisposition(header)).toBe("상품매입장부_2026년_11월.xlsx");
    expect(fileNameFromDisposition('inline; filename="a.pdf"')).toBe("a.pdf");
    expect(fileNameFromDisposition(null)).toBeNull();
  });

  it("builds query strings without empty values", () => {
    expect(query({ a: "1", b: "", c: null, d: undefined, e: 0, f: false })).toBe("?a=1&e=0&f=false");
    expect(query({})).toBe("");
  });

  it("formats won and fractional rates", () => {
    expect(won(37900)).toBe("37,900원");
    expect(won(null)).toBe("-");
    expect(pct(0.4)).toBe("40.0%");
    expect(pct("0.5525", 2)).toBe("55.25%");
    expect(pct("0.5215")).toBe("52.2%");
    expect(pct(-0.0005)).toBe("-0.1%");
    expect(pct(0)).toBe("0.0%");
  });

  it("escapes AI text before turning it into editor HTML", () => {
    expect(textToHtml("<script>x</script>\n줄바꿈\n\n두번째")).toBe(
      "<p>&lt;script&gt;x&lt;/script&gt;<br>줄바꿈</p><p>두번째</p>",
    );
  });

  it("highlights the most specific admin menu", () => {
    expect(activeLink("/admin/products/new")?.label).toBe("상품 등록 (AI)");
    expect(activeLink("/admin/products/12/edit")?.label).toBe("상품 목록");
    expect(activeLink("/admin/purchases/15")?.label).toBe("매입장부");
    expect(activeLink("/admin/inventory/status")?.label).toBe("재고 현황");
    expect(activeLink("/admin")?.label).toBe("운영 현황");
  });
});
