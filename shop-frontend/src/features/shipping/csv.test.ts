import { describe, expect, it } from "vitest";
import { decodeCsv, parseInvoiceCsv } from "./csv";

describe("invoice CSV parsing", () => {
  it("reads English headers in any column order", () => {
    const { rows, errors } = parseInvoiceCsv(
      "trackingNumber,orderNumber,deliveryCompany\n1234-5678-9012,ORD-1,CJ\n",
    );
    expect(errors).toEqual([]);
    expect(rows).toEqual([{ orderNumber: "ORD-1", deliveryCompany: "CJ", trackingNumber: "123456789012" }]);
  });

  it("reads Korean headers and quoted cells", () => {
    const { rows } = parseInvoiceCsv('주문번호,택배사,송장번호\r\n"ORD-2","CJ대한통운"," 5566 7788 "\r\n');
    expect(rows).toEqual([{ orderNumber: "ORD-2", deliveryCompany: "CJ대한통운", trackingNumber: "55667788" }]);
  });

  it("uses positional columns without a header", () => {
    const { rows } = parseInvoiceCsv("ORD-3,HANJIN,111122223333\nORD-4\thanjin\t999988887777");
    expect(rows.map((r) => r.orderNumber)).toEqual(["ORD-3", "ORD-4"]);
    expect(rows[1].deliveryCompany).toBe("hanjin");
  });

  it("reports incomplete rows and empty files", () => {
    const { rows, errors } = parseInvoiceCsv("orderNumber,deliveryCompany,trackingNumber\nORD-5,CJ,\n");
    expect(rows).toEqual([]);
    expect(errors).toHaveLength(1);
    expect(parseInvoiceCsv("  \n").errors).toEqual(["파일이 비어 있습니다."]);
  });

  it("decodes UTF-8 with BOM", () => {
    const bytes = new Uint8Array([0xef, 0xbb, 0xbf, ...new TextEncoder().encode("주문번호")]);
    expect(decodeCsv(bytes.buffer)).toBe("주문번호");
  });
});
