export type RecoSettings = {
  enabled: boolean;
  homeEnabled: boolean;
  detailEnabled: boolean;
  cartEnabled: boolean;
  mypageEnabled: boolean;
  displayCount: number;
  cacheMinutes: number;
  minStock: number;
  attributionDays: number;
  popularDays: number;
  wCategory: number;
  wColor: number;
  wPrice: number;
  wPurchase: number;
  wPopularity: number;
  wFreshness: number;
  maxDiscountRate: number;
  updatedAt: string | null;
};

export const WEIGHT_FIELDS: { key: keyof RecoSettings; label: string; formula: string }[] = [
  { key: "wCategory", label: "카테고리 일치", formula: "categoryMatch" },
  { key: "wColor", label: "색상 일치", formula: "colorMatch" },
  { key: "wPrice", label: "가격대 일치", formula: "priceMatch" },
  { key: "wPurchase", label: "구매 유사도", formula: "purchaseSimilarity" },
  { key: "wPopularity", label: "인기도", formula: "popularity" },
  { key: "wFreshness", label: "신상품도", formula: "freshness" },
];

export type RecoRule = {
  id: number;
  ruleType: string;
  sourceValue: string | null;
  targetValue: string | null;
  productId: number | null;
  weight: number;
  memo: string | null;
  active: boolean;
  updatedAt: string;
};

export type RecoRuleRequest = {
  ruleType: string;
  sourceValue: string | null;
  targetValue: string | null;
  productId: number | null;
  weight: number | null;
  memo: string | null;
  active: boolean;
};

export const RULE_TYPE_LABELS: Record<string, string> = {
  COORDINATION: "코디 규칙",
  EXCLUDE_PRODUCT: "추천 제외 상품",
  CATEGORY_BOOST: "카테고리 가중치",
};

export type PreviewItem = {
  rank: number;
  productId: number;
  productName: string;
  group: string | null;
  price: number;
  stock: number;
  score: number;
  breakdown: Record<string, number>;
  reason: string | null;
};

export type RecoPreview = { type: string; algorithm: string; basis: string; items: PreviewItem[] };

export const BREAKDOWN_LABELS: Record<string, string> = {
  categoryMatch: "카테고리",
  colorMatch: "색상",
  priceMatch: "가격",
  purchaseSimilarity: "구매유사",
  popularity: "인기",
  freshness: "신상",
  categoryRule: "코디규칙",
  styleMatch: "스타일",
  similarity: "유사도",
};

export const POPULAR_TYPE_LABELS: Record<string, string> = {
  TOP10: "전체 TOP 10",
  TOP_TOPS: "상의 인기",
  TOP_BOTTOMS: "하의 인기",
  NEW_POPULAR: "신상품 인기",
  TRENDING: "급상승",
};

export type PurchaseRow = {
  productId: number;
  productName: string;
  skuId: number;
  skuCode: string;
  optionName: string;
  category: string | null;
  stock: number;
  available: number;
  pendingInbound: number;
  sales7: number;
  sales30: number;
  dailySales: number;
  trend: string;
  trendRate: number | null;
  turnover: number | null;
  coverDays: number | null;
  leadDays: number;
  safetyDays: number;
  targetDays: number;
  unitCost: number | null;
  price: number | null;
  marginRate: number | null;
  suggestedQuantity: number;
  priority: number;
  decision: string;
  formula: string;
  reasons: string[];
};

export type PurchaseReport = { asOf: string; criteria: string[]; rows: PurchaseRow[] };

export const TREND_LABELS: Record<string, string> = {
  UP: "증가",
  DOWN: "감소",
  FLAT: "보합",
  NEW_DEMAND: "신규 수요",
  NONE: "판매 없음",
};

export const PURCHASE_DECISION_LABELS: Record<string, string> = {
  REORDER: "발주 검토",
  ENOUGH: "재고 충분",
  NO_SALES: "판매 없음",
};

export type SlowRow = {
  productId: number;
  productName: string;
  category: string | null;
  status: string;
  stock: number;
  sales30: number;
  sales90: number;
  lastSoldDate: string | null;
  daysSinceLastSale: number | null;
  daysOnHand: number;
  coverDays: number | null;
  season: string | null;
  unitCost: number | null;
  price: number | null;
  marginRate: number | null;
  stockValue: number | null;
  classes: string[];
  strategies: string[];
  suggestedDiscountRate: number | null;
  maxDiscountRate: number | null;
  suggestedPrice: number | null;
  floorPrice: number | null;
  marginAfterDiscount: number | null;
  reasons: string[];
};

export type SlowReport = {
  asOf: string;
  minimumMarginRate: number;
  maxDiscountRate: number;
  criteria: string[];
  rows: SlowRow[];
};

export const SLOW_CLASS_LABELS: Record<string, string> = {
  LONG_UNSOLD: "장기 미판매",
  OVERSTOCK: "과잉 재고",
  SEASON_ENDING: "시즌 종료 임박",
  HIGH_MARGIN_LOW_SALES: "고마진 저판매",
  DISCOUNT_REVIEW: "할인 검토",
};

export type DecisionView = {
  id: number;
  type: string;
  productId: number;
  skuId: number | null;
  status: string;
  quantity: number | null;
  reason: string;
  decidedAt: string;
};

export type PlacementStats = {
  placement: string;
  variant: string;
  impressions: number;
  clicks: number;
  carts: number;
  purchases: number;
  revenue: number;
  ctr: number;
  cartRate: number;
  conversionRate: number;
};

export type RecoAnalytics = {
  from: string;
  to: string;
  attributionDays: number;
  placements: PlacementStats[];
  daily: { date: string; impressions: number; clicks: number; carts: number; purchases: number; revenue: number }[];
  impressions: number;
  clicks: number;
  carts: number;
  purchases: number;
  recommendationRevenue: number;
  totalRevenue: number;
  normalRevenue: number;
  revenueContribution: number;
  notes: string[];
};

export const PLACEMENT_LABELS: Record<string, string> = {
  HOME_PERSONAL: "메인 · 맞춤 추천",
  HOME_POPULAR: "메인 · 인기 상품",
  HOME_NEW: "메인 · 신상품",
  DETAIL_SIMILAR: "상세 · 비슷한 상품",
  DETAIL_COORDI: "상세 · 코디 추천",
  DETAIL_PRICE: "상세 · 비슷한 가격대",
  CART_MATCH: "장바구니 · 어울리는 상품",
  MYPAGE_TASTE: "마이페이지 · 취향 추천",
  MYPAGE_RECENT: "마이페이지 · 최근 본 상품 유사",
};

export type FeatureView = {
  productId: number;
  productName: string;
  status: string;
  source: string | null;
  group: string | null;
  itemType: string | null;
  colors: string[];
  colorTone: string | null;
  styles: string[];
  designs: string[];
  seasons: string[];
  material: string | null;
  aiProvider: string | null;
  analyzedAt: string | null;
};

export type FeatureManualRequest = {
  categoryGroup: string | null;
  itemType: string | null;
  colors: string | null;
  colorTone: string | null;
  styleTags: string | null;
  designTags: string | null;
  season: string | null;
  material: string | null;
  keywords: string | null;
};

export const FEATURE_SOURCE_LABELS: Record<string, string> = {
  RULE: "규칙(자동)",
  AI: "AI 분석",
  MANUAL: "수동 입력",
};

export const GROUP_LABELS: Record<string, string> = {
  TOP: "상의",
  BOTTOM: "하의",
  DRESS: "원피스",
  OUTER: "아우터",
  ETC: "기타",
};
