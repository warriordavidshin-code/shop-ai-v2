export type InvoiceRow = {
  orderNumber: string;
  deliveryCompany: string;
  trackingNumber: string;
};

export type InvoiceCsvResult = {
  rows: InvoiceRow[];
  errors: string[];
};

const HEADER_ALIASES: Record<keyof InvoiceRow, string[]> = {
  orderNumber: ["ordernumber", "orderno", "주문번호"],
  deliveryCompany: ["deliverycompany", "company", "택배사"],
  trackingNumber: ["trackingnumber", "invoice", "송장번호", "운송장번호"],
};

/** Decodes an uploaded file as UTF-8, falling back to EUC-KR (Excel's default Korean CSV encoding). */
export function decodeCsv(buffer: ArrayBuffer): string {
  const utf8 = new TextDecoder("utf-8").decode(buffer);
  if (!utf8.includes("\uFFFD")) return utf8.replace(/^\uFEFF/, "");
  try {
    return new TextDecoder("euc-kr").decode(buffer);
  } catch {
    return utf8;
  }
}

function splitLine(line: string): string[] {
  const cells: string[] = [];
  let current = "";
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (quoted) {
      if (ch === '"' && line[i + 1] === '"') {
        current += '"';
        i++;
      } else if (ch === '"') {
        quoted = false;
      } else {
        current += ch;
      }
    } else if (ch === '"') {
      quoted = true;
    } else if (ch === "," || ch === "\t") {
      cells.push(current.trim());
      current = "";
    } else {
      current += ch;
    }
  }
  cells.push(current.trim());
  return cells;
}

/**
 * Parses "orderNumber,deliveryCompany,trackingNumber" rows (Korean headers accepted).
 * Without a recognised header the first three columns are used in that order.
 */
export function parseInvoiceCsv(text: string): InvoiceCsvResult {
  const lines = text.split(/\r?\n/).filter((line) => line.trim() !== "");
  if (lines.length === 0) return { rows: [], errors: ["파일이 비어 있습니다."] };

  const header = splitLine(lines[0]).map((cell) => cell.replace(/\s/g, "").toLowerCase());
  const index: Record<keyof InvoiceRow, number> = { orderNumber: 0, deliveryCompany: 1, trackingNumber: 2 };
  let hasHeader = false;
  for (const key of Object.keys(HEADER_ALIASES) as (keyof InvoiceRow)[]) {
    const found = header.findIndex((cell) => HEADER_ALIASES[key].includes(cell));
    if (found >= 0) {
      index[key] = found;
      hasHeader = true;
    }
  }

  const rows: InvoiceRow[] = [];
  const errors: string[] = [];
  lines.slice(hasHeader ? 1 : 0).forEach((line, i) => {
    const cells = splitLine(line);
    const row: InvoiceRow = {
      orderNumber: cells[index.orderNumber] ?? "",
      deliveryCompany: cells[index.deliveryCompany] ?? "",
      trackingNumber: (cells[index.trackingNumber] ?? "").replace(/[\s-]/g, ""),
    };
    if (!row.orderNumber || !row.deliveryCompany || !row.trackingNumber) {
      errors.push(`${i + 1}번째 행: 주문번호/택배사/송장번호가 비어 있습니다.`);
      return;
    }
    rows.push(row);
  });
  return { rows, errors };
}
