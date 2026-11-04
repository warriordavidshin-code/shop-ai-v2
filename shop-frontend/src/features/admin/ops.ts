import { ApiError, parseApiError, shopFetch } from "@/lib/api/client";

export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export async function adminJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await shopFetch(path, init);
  if (!response.ok) throw await parseApiError(response);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export function postJson<T>(path: string, body: unknown, method = "POST"): Promise<T> {
  return adminJson<T>(path, { method, body: JSON.stringify(body ?? {}) });
}

export function postForm<T>(path: string, form: FormData): Promise<T> {
  return adminJson<T>(path, { method: "POST", body: form });
}

export function query(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === "") continue;
    search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

/** Downloads a binary admin response, keeping the server-provided (RFC 5987) filename. */
export async function downloadFile(path: string, fallbackName: string): Promise<{ rows: number | null }> {
  const response = await shopFetch(path);
  if (!response.ok) throw await parseApiError(response);
  const blob = await response.blob();
  const name = fileNameFromDisposition(response.headers.get("content-disposition")) ?? fallbackName;
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  const rows = response.headers.get("x-export-rows");
  return { rows: rows === null ? null : Number(rows) };
}

export function fileNameFromDisposition(header: string | null): string | null {
  if (!header) return null;
  const star = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (star) {
    try {
      return decodeURIComponent(star[1].trim());
    } catch {
      return null;
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain ? plain[1].trim() : null;
}

export function won(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === "") return "-";
  const n = Number(value);
  if (!Number.isFinite(n)) return "-";
  return `${new Intl.NumberFormat("ko-KR").format(Math.round(n))}원`;
}

export function num(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === "") return "-";
  const n = Number(value);
  return Number.isFinite(n) ? new Intl.NumberFormat("ko-KR").format(n) : "-";
}

/** Rates arrive as fractions (0.5 = 50%). */
export function pct(value: number | string | null | undefined, digits = 1): string {
  if (value === null || value === undefined || value === "") return "-";
  const n = Number(value);
  if (!Number.isFinite(n)) return "-";
  // Match the backend's BigDecimal HALF_UP (toFixed rounds 52.15 down to 52.1).
  const scale = 10 ** digits;
  const scaled = Number((Math.abs(n) * 100 * scale).toPrecision(12));
  return `${((Math.sign(n) * Math.round(scaled)) / scale).toFixed(digits)}%`;
}

export function dateTime(value: string | null | undefined): string {
  if (!value) return "-";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString("ko-KR", { dateStyle: "short", timeStyle: "short" });
}

export function errorMessage(e: unknown, fallback = "요청을 처리하지 못했습니다."): string {
  return e instanceof Error && e.message ? e.message : fallback;
}

// ---------- 매입장부 ----------

export const KEPT_LABELS: Record<string, string> = { KEPT: "보관", NOT_KEPT: "미보관", NOT_APPLICABLE: "해당없음" };
export const EVIDENCE_TYPE_LABELS: Record<string, string> = {
  NONE: "없음",
  TAX_INVOICE: "세금계산서",
  CASH_RECEIPT: "현금영수증(지출증빙)",
  CARD_SLIP: "카드전표",
  INVOICE: "계산서",
};
export const PAYMENT_METHOD_LABELS: Record<string, string> = {
  CASH: "현금",
  TRANSFER: "계좌이체",
  CARD: "카드",
  OTHER: "기타",
};
export const EVIDENCE_CATEGORY_LABELS: Record<string, string> = {
  STATEMENT: "거래명세표",
  CASH_RECEIPT: "현금영수증",
  CARD_RECEIPT: "카드영수증",
  TRANSFER: "이체내역",
  TAX_INVOICE: "세금계산서",
  OTHER: "기타",
};
export const MATCH_STATUS_LABELS: Record<string, string> = {
  UNMATCHED: "미매칭",
  MATCHED: "매칭됨",
  CANDIDATE: "후보 확인 필요",
  NEW_PRODUCT: "신규 상품 후보",
  IGNORED: "제외",
};
export const COST_SOURCE_LABELS: Record<string, string> = {
  INPUT: "직접 입력",
  PURCHASE_STATEMENT: "매입 명세 단가",
  RECENT: "최근 매입원가",
  WEIGHTED_AVERAGE: "이동평균원가",
  STANDARD: "표준원가",
  RECENT_FALLBACK: "최근 매입원가(대체)",
  WEIGHTED_AVERAGE_FALLBACK: "이동평균원가(대체)",
  STANDARD_FALLBACK: "표준원가(대체)",
  UNKNOWN: "원가 미확인",
};
export const EVIDENCE_AI_STATUS_LABELS: Record<string, string> = {
  NOT_ANALYZED: "미분석",
  ANALYZED: "분석 완료",
  FAILED: "분석 실패",
};

export type LedgerItem = {
  id: number | null;
  lineNo?: number;
  productName: string;
  optionName: string | null;
  color: string | null;
  size: string | null;
  supplierProductCode: string | null;
  quantity: number | null;
  unitPrice: number | null;
  supplyAmount: number | null;
  taxAmount: number | null;
  totalAmount: number | null;
  matchStatus?: string;
  matchedProductId?: number | null;
  matchedProductName?: string | null;
  matchedSkuId?: number | null;
  matchedOptionName?: string | null;
  matchScore?: number | null;
  matchNote?: string | null;
  receivedQuantity?: number;
  remainingQuantity?: number;
};

export type Evidence = {
  id: number;
  purchaseLedgerId: number | null;
  managementNo: string | null;
  originalFileName: string;
  contentType: string;
  fileSize: number;
  fileHash: string;
  category: string;
  categoryLabel: string;
  aiAnalysisStatus: string;
  createdAt: string;
  fileUrl: string;
};

export type LedgerRow = {
  id: number;
  managementNo: string;
  purchaseDate: string;
  supplierId: number | null;
  supplierName: string | null;
  supplierBusinessNo: string | null;
  itemSummary: string | null;
  purchaseAmount: number | null;
  supplyAmount: number | null;
  taxAmount: number | null;
  paymentAmount: number | null;
  paymentDate: string | null;
  paymentMethod: string | null;
  recipientName: string | null;
  transferReference: string | null;
  differenceAmount: number | null;
  statementKept: string | null;
  transferKept: string | null;
  evidenceType: string | null;
  evidenceLocation: string | null;
  remarks: string | null;
  verificationStatus: string;
  verificationLabel: string;
  missingFields: string[];
  entrySource: string;
  evidenceCount: number;
  itemCount: number;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type LedgerDetail = {
  ledger: LedgerRow;
  items: LedgerItem[];
  evidences: Evidence[];
  receipts: { id: number; receiptNo: string; status: string; confirmedAt: string | null }[];
  sourceAnalysisId: number | null;
};

export type LedgerRequest = {
  purchaseDate: string;
  supplierId: number | null;
  supplierName: string | null;
  supplierBusinessNo: string | null;
  itemSummary: string | null;
  purchaseAmount: number | null;
  supplyAmount: number | null;
  taxAmount: number | null;
  paymentAmount: number | null;
  paymentDate: string | null;
  paymentMethod: string | null;
  recipientName: string | null;
  transferReference: string | null;
  statementKept: string | null;
  transferKept: string | null;
  evidenceType: string | null;
  evidenceLocation: string | null;
  remarks: string | null;
  items: LedgerItem[];
  evidenceIds: number[];
  sourceAnalysisId: number | null;
  confirmDuplicate: boolean;
  version: number | null;
};

export type DuplicateCandidate = {
  ledgerId: number;
  managementNo: string;
  purchaseDate: string;
  supplierName: string;
  purchaseAmount: number;
  reasons: string[];
};

export type MonthSummary = {
  month: number;
  purchaseAmount: number;
  paymentAmount: number;
  purchaseCount: number;
  statementKeptCount: number;
  transferKeptCount: number;
  differenceAmount: number;
  needsReviewCount: number;
  missingEvidenceCount: number;
};

export type SummaryResponse = {
  year: number;
  months: MonthSummary[];
  total: MonthSummary;
  suppliers: { supplierName: string; purchaseCount: number; purchaseAmount: number; paymentAmount: number }[];
  unlinkedEvidenceCount: number;
};

export type ReceiptAnalysis = {
  analysisId: number | null;
  provider: string;
  model: string | null;
  mock: boolean;
  cached: boolean;
  estimatedCostUsd: number | null;
  evidences: Evidence[];
  documents: {
    fileIndex: number;
    evidenceId: number | null;
    fileName: string;
    documentType: string;
    documentLabel: string;
    confidence: number | null;
  }[];
  draft: LedgerRequest;
  fieldConfidence: Record<string, number>;
  reviewFields: { field: string; reason: string }[];
  warnings: string[];
  duplicates: DuplicateCandidate[];
  billingType?: string | null;
  notices?: string[];
  paidApprovalAvailable?: boolean;
};

export type Candidate = {
  productId: number;
  productName: string;
  status: string;
  score: number;
  reasons: string[];
  skus: { skuId: number; skuCode: string; color: string; size: string; stockQuantity: number; optionMatches: boolean }[];
};

export type UnmatchedItem = {
  itemId: number;
  ledgerId: number;
  managementNo: string;
  supplierName: string | null;
  purchaseDate: string;
  productName: string;
  color: string | null;
  size: string | null;
  supplierProductCode: string | null;
  quantity: number | null;
  unitPrice: number | null;
  matchStatus: string;
  matchScore: number | null;
  matchNote: string | null;
};

export type MatchAction = "AUTO" | "CONFIRM" | "NEW_PRODUCT" | "IGNORE" | "RESET";

export function matchItems(body: {
  ledgerId?: number;
  itemId?: number;
  action: MatchAction;
  productId?: number;
  skuId?: number;
  remember?: boolean;
}) {
  return postJson<UnmatchedItem[]>("/admin/purchases/items/match", body);
}

export type Supplier = {
  id: number;
  supplierName: string;
  businessNo: string | null;
  contactName: string | null;
  phone: string | null;
  address: string | null;
  leadDays: number | null;
  averageLeadDays: number | null;
  memo: string | null;
  active: boolean;
  purchaseCount: number;
  purchaseAmount: number;
  mappedProductCount: number;
};

export type SupplierMapping = {
  id: number;
  supplierProductCode: string | null;
  supplierProductName: string | null;
  productId: number;
  skuId: number | null;
  color: string | null;
  size: string | null;
  lastUnitCost: number | null;
  lastPurchaseDate: string | null;
};

// ---------- 입고 / 재고 ----------

export type ReceiptItem = {
  id: number;
  purchaseLedgerItemId: number;
  productId: number;
  productName: string;
  skuId: number;
  optionName: string;
  orderedQuantity: number;
  expectedQuantity: number;
  receivedQuantity: number | null;
  ledgerReceivedQuantity: number;
  ledgerRemainingQuantity: number;
  unitCost: number;
  allocatedCost: number | null;
  landedUnitCost: number | null;
  currentStock: number;
};

export type Receipt = {
  id: number;
  receiptNo: string;
  purchaseLedgerId: number;
  managementNo: string;
  supplierName: string | null;
  status: string;
  expectedDate: string | null;
  freightCost: number;
  incidentalCost: number;
  packagingCost: number;
  otherCost: number;
  additionalCostTotal: number;
  allocationMethod: string;
  memo: string | null;
  confirmedAt: string | null;
  confirmedBy: number | null;
  createdAt: string;
  items: ReceiptItem[];
  warnings: string[];
  alreadyConfirmed: boolean;
};

export const RECEIPT_STATUS_LABELS: Record<string, string> = {
  PENDING: "입고 대기",
  CONFIRMED: "입고 확정",
  CANCELLED: "취소",
};

export type TransactionRow = {
  id: number;
  createdAt: string;
  movementType: string;
  movementLabel: string;
  productId: number;
  productName: string;
  skuId: number;
  skuCode: string;
  optionName: string;
  quantity: number;
  stockBefore: number | null;
  stockAfter: number | null;
  referenceType: string | null;
  referenceId: string | null;
  purchaseReceiptId: number | null;
  receiptNo: string | null;
  managementNo: string | null;
  unitCost: number | null;
  reason: string | null;
  actorMemberId: number | null;
  actorName: string | null;
};

export type StockRow = {
  productId: number;
  productName: string;
  productStatus: string;
  skuId: number;
  skuCode: string;
  optionName: string;
  stockQuantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  reorderPoint: number;
  pendingInbound: number;
  unitCost: number | null;
  costSource: string | null;
  stockValue: number | null;
  stockStatus: string;
};

export type StockStatus = {
  skuCount: number;
  totalQuantity: number;
  totalValue: number;
  soldOutCount: number;
  lowStockCount: number;
  unknownCostCount: number;
  rows: StockRow[];
};

export type LowStockRow = {
  productId: number;
  productName: string;
  skuId: number;
  skuCode: string;
  optionName: string;
  availableQuantity: number;
  reorderPoint: number;
  pendingInbound: number;
  salesInWindow: number;
  windowDays: number;
  dailySales: number;
  coverDays: number | null;
  leadDays: number;
  safetyStock: number;
  reorderLevel: number;
  suggestedQuantity: number;
  supplierId: number | null;
  supplierName: string | null;
  reasons: string[];
};

export type SlowMovingRow = {
  productId: number;
  productName: string;
  skuId: number;
  skuCode: string;
  optionName: string;
  stockQuantity: number;
  salesInPeriod: number;
  periodDays: number;
  lastSoldDate: string | null;
  lastReceivedDate: string | null;
  daysSinceLastSale: number | null;
  stockValue: number | null;
  reason: string;
};

export type Analysis<T> = { asOf: string; windowDays: number; criteria: string[]; rows: T[] };

// ---------- 가격 / 손익 ----------

export type PricingPolicy = {
  competitiveMarginRate: number;
  standardMarginRate: number;
  premiumMarginRate: number;
  minimumMarginRate: number;
  roundingMode: string;
  marginBasis: string;
  taxType: string;
  vatRate: number;
  costingMethod: string;
  paymentFeeRate: number;
  shippingCostPerOrder: number;
  otherSellingCostRate: number;
  lowStockCoverDays: number;
  safetyStockDays: number;
  defaultLeadDays: number;
  salesWindowDays: number;
  slowMovingDays: number;
  slowMovingMaxSales: number;
  updatedAt: string | null;
  version: number | null;
};

export type PriceOption = {
  tier: string;
  label: string;
  targetMarginRate: number;
  rawPrice: number;
  roundedPrice: number;
  marginRate: number;
  grossProfit: number;
  belowMinimum: boolean;
  note: string | null;
};

export type PriceRecommendation = {
  unitCost: number | null;
  costSource: string | null;
  marginBasis: string;
  roundingMode: string;
  minimumMarginRate: number;
  options: PriceOption[];
  current: { salePrice: number; marginRate: number; grossProfit: number; belowMinimum: boolean } | null;
  warnings: string[];
  explanation: string | null;
};

export function recommendPrice(body: { unitCost?: number | null; productId?: number | null; currentSalePrice?: number | null }) {
  return postJson<PriceRecommendation>("/admin/pricing/recommend", body);
}

export type ProfitLine = {
  productId: number;
  productName: string;
  categoryId: number | null;
  categoryName: string | null;
  soldQuantity: number;
  refundedQuantity: number;
  grossSales: number;
  discountAmount: number;
  refundAmount: number;
  netSales: number;
  costOfGoodsSold: number;
  grossProfit: number;
  grossMarginRate: number | null;
  paymentFee: number;
  shippingCost: number;
  deliveryRevenue: number;
  otherSellingCost: number;
  contributionProfit: number;
  contributionMarginRate: number | null;
  estimatedCostQuantity: number;
};

export type ProfitTotals = Omit<ProfitLine, "productId" | "productName" | "categoryId" | "categoryName"> & {
  orderCount: number;
};

export type ProductProfit = {
  period: { code: string; from: string; to: string };
  revenueBasis: string;
  totals: ProfitTotals;
  products: ProfitLine[];
  notes: string[];
};

export type MonthlyProfit = {
  month: string;
  purchaseAmount: number;
  paymentAmount: number;
  netSales: number;
  costOfGoodsSold: number;
  grossProfit: number;
  grossMarginRate: number | null;
  contributionProfit: number;
  soldQuantity: number;
};

export type CategorySales = {
  categoryId: number | null;
  categoryName: string | null;
  netSales: number;
  grossProfit: number;
  soldQuantity: number;
  share: number | null;
};

export type OperationsDashboard = {
  asOf: string;
  month: string;
  purchases: {
    purchaseAmount: number;
    paymentAmount: number;
    unpaidThisMonth: number;
    unpaidTotal: number;
    previousPurchaseAmount: number;
    changeRate: number | null;
  };
  stock: {
    totalQuantity: number;
    stockValue: number;
    soldOutCount: number;
    lowStockCount: number;
    slowMovingCount: number;
    unknownCostCount: number;
    pendingReceiptCount: number;
  };
  sales: {
    netSales: number;
    soldQuantity: number;
    grossProfit: number;
    grossMarginRate: number | null;
    contributionProfit: number;
    previousNetSales: number;
    netSalesChangeRate: number | null;
    previousGrossProfit: number;
    orderCount: number;
  };
  ai: {
    callsThisMonth: number;
    failuresThisMonth: number;
    inputTokens: number;
    outputTokens: number;
    estimatedCostUsd: number | null;
    available: boolean;
    mockMode: boolean;
  };
  monthlyTrend: MonthlyProfit[];
  categoryShare: CategorySales[];
  productRanking: ProfitLine[];
  supplierPurchases: { name: string; value: number }[];
  lowStockItems: {
    skuId: number;
    productName: string;
    optionName: string;
    available: number;
    suggestedQuantity: number;
    coverDays: number | null;
  }[];
  notes: string[];
};

// ---------- AI 운영 ----------

export type AiStatus = {
  receiptEnabled: boolean;
  productEnabled: boolean;
  apiKeyConfigured: boolean;
  mockMode: boolean;
  available: boolean;
  model: string | null;
  provider: string | null;
  mode?: string;
  geminiKeyConfigured?: boolean;
  localOcrEnabled?: boolean;
  openaiEnabled?: boolean;
  paidFallbackEnabled?: boolean;
};

export const AI_MODE_LABELS: Record<string, string> = {
  FREE_FIRST: "무료 우선 (로컬 OCR → Gemini 무료 → 승인 시 OpenAI)",
  GEMINI: "Gemini만 사용",
  OPENAI: "OpenAI 사용 (유료)",
  LOCAL_ONLY: "로컬 OCR만 사용 (외부 AI 호출 없음)",
  MANUAL: "수동 입력 (AI 사용 안 함)",
};

export const BILLING_LABELS: Record<string, string> = {
  FREE: "무료",
  PAID: "유료",
  LOCAL: "로컬(무료)",
  NONE: "비용 없음",
};

export const PROVIDER_LABELS: Record<string, string> = {
  LOCAL_OCR: "로컬 OCR",
  GEMINI: "Gemini",
  OPENAI: "OpenAI",
  MOCK: "Mock",
  RULE: "규칙",
};

/** Backend 409 asking the admin to explicitly approve a paid (OpenAI) call. */
export function isPaidApprovalRequired(e: unknown): boolean {
  return e instanceof ApiError && e.code === "AI_PAID_APPROVAL_REQUIRED";
}

export type AiEffectiveSettings = {
  mode: string;
  localOcrEnabled: boolean;
  geminiEnabled: boolean;
  openaiEnabled: boolean;
  paidFallbackEnabled: boolean;
  cacheEnabled: boolean;
  dailyRequestLimit: number;
  monthlyBudgetUsd: number;
  geminiKeyConfigured: boolean;
  openaiKeyConfigured: boolean;
  geminiFreeTier: boolean;
  mockEnabled: boolean;
  geminiModel: string | null;
  openaiModel: string | null;
};

export type AiOverrides = {
  mode: string | null;
  localOcrEnabled: boolean | null;
  geminiEnabled: boolean | null;
  openaiEnabled: boolean | null;
  paidFallbackEnabled: boolean | null;
  cacheEnabled: boolean | null;
  dailyRequestLimit: number | null;
  monthlyBudgetUsd: number | null;
};

export type AiSettingsView = {
  effective: AiEffectiveSettings;
  overrides: AiOverrides;
  environmentDefaults: Record<string, string | number | boolean>;
  localOcrHealthy: boolean;
  modes: string[];
};

export type AiProviderUsage = {
  provider: string;
  model: string | null;
  enabled: boolean;
  keyConfigured: boolean;
  billing: string;
  todayCalls: number;
  todaySuccess: number;
  todayFailures: number;
  todayCacheHits: number;
  todayBlocked: number;
  todayInputTokens: number;
  todayOutputTokens: number;
  todayEstimatedCostUsd: number | null;
  monthCalls: number;
  monthCacheHits: number;
  monthInputTokens: number;
  monthOutputTokens: number;
  monthEstimatedCostUsd: number | null;
  totalEstimatedCostUsd: number | null;
  quotaExhaustedToday: boolean;
};

export type AiBillingRecord = {
  id: number;
  provider: string;
  billingMonth: string;
  amountUsd: number;
  memo: string | null;
  recordedAt: string;
};

export type AiCostUsage = {
  today: string;
  month: string;
  mode: string;
  paidApiEnabled: boolean;
  paidFallbackEnabled: boolean;
  cacheEnabled: boolean;
  dailyRequestLimit: number;
  dailyRequestsUsed: number;
  dailyLimitReached: boolean;
  budget: {
    month: string;
    budgetUsd: number;
    spentUsd: number;
    reservedUsd: number;
    remainingUsd: number;
    blockedCount: number;
    exceeded: boolean;
  };
  providers: AiProviderUsage[];
  monthEstimatedCostUsd: number | null;
  monthActualBilledUsd: number | null;
  billingRecords: AiBillingRecord[];
  alerts: string[];
  note: string;
};

export function usd(value: number | string | null | undefined, digits = 4): string {
  if (value === null || value === undefined || value === "") return "-";
  const n = Number(value);
  return Number.isFinite(n) ? `$${n.toFixed(digits)}` : "-";
}

export type AiAnalysisRow = {
  id: number;
  type: string;
  provider: string;
  model: string | null;
  status: string;
  errorCode: string | null;
  errorMessage: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  estimatedCostUsd: number | null;
  durationMs: number | null;
  attempts: number;
  fileCount: number;
  targetType: string | null;
  targetId: number | null;
  createdAt: string;
  billingType?: string | null;
};

export type AiUsage = {
  from: string;
  calls: number;
  failures: number;
  inputTokens: number;
  outputTokens: number;
  estimatedCostUsd: number | null;
  note: string | null;
};

export type Recommendation = {
  id: number;
  type: string;
  productId: number | null;
  productName: string | null;
  skuId: number | null;
  optionName: string | null;
  priority: number;
  suggestedQuantity: number | null;
  metrics: Record<string, unknown> | null;
  reason: string;
  provider: string;
  status: string;
  createdAt: string;
  decidedAt: string | null;
};

export type ProductAnalysis = {
  analysisId: number | null;
  provider: string;
  model: string | null;
  mock: boolean;
  cached: boolean;
  estimatedCostUsd: number | null;
  productNameCandidates: string[];
  categoryId: number | null;
  categoryName: string | null;
  categoryReason: string | null;
  summary: string | null;
  description: string | null;
  colors: string[];
  style: string | null;
  season: string | null;
  materialEstimate: string | null;
  fitType: string | null;
  thickness: string | null;
  stretch: string | null;
  seeThrough: string | null;
  keywords: string[];
  seoDescription: string | null;
  altTexts: { imageIndex: number; imageUrl: string | null; altText: string }[];
  confidence: number | null;
  needsConfirmation: string[];
  warnings: string[];
  similarProducts: { productId: number; productName: string; score: number }[];
  billingType?: string | null;
  notices?: string[];
};

export type ProductPrepare = {
  purchase: {
    ledgerItemId: number;
    ledgerId: number;
    managementNo: string;
    supplierName: string | null;
    productName: string;
    color: string | null;
    size: string | null;
    supplierProductCode: string | null;
    quantity: number | null;
    unitCost: number | null;
    matchStatus: string;
  };
  analysis: ProductAnalysis | null;
  pricing: PriceRecommendation | null;
  draft: {
    productName: string;
    summary: string | null;
    description: string | null;
    categoryId: number | null;
    material: string | null;
    season: string | null;
    fitType: string | null;
    thickness: string | null;
    stretch: string | null;
    seeThrough: string | null;
    searchKeywords: string | null;
    seoDescription: string | null;
    purchasePrice: number | null;
    normalPrice: number | null;
    salePrice: number | null;
    skus: { color: string; size: string }[];
  };
  needsConfirmation: string[];
  warnings: string[];
};
