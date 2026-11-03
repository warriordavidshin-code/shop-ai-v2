# 상품매입장부 · AI 분석 · 입고/재고 · 가격 · 손익 운영 안내

관리자 전용 기능입니다. 모든 API는 `/api/admin/**` 아래에 있으며 ADMIN 권한이 필요합니다.

## 1. 전체 흐름

```
영수증·명세표 업로드 → AI 분석(초안) → 관리자 검토·저장(매입장부)
  → 상품 매칭(기존 SKU / 신규 상품 등록) → 입고 등록(재고 반영 전)
  → 입고 확정(재고 증가 + 원가 이력) → 판매 시 원가 스냅샷 → 손익 분석
```

- 매입장부에 저장해도 **재고는 늘지 않습니다**. 재고는 `입고 확정` 시에만 증가합니다.
- 같은 입고건을 여러 번 확정해도 재고는 한 번만 늘어납니다(`대기→확정` 조건부 UPDATE로 한 요청만 통과 + `inventory_movement` 입고 라인 유니크 인덱스).
- AI 결과는 항상 초안입니다. 관리자가 저장하기 전에는 매입장부·상품이 생성되지 않으며, 신규 상품은 기본 `DRAFT`(비공개)로 만들어집니다.
- 발주 추천은 참고용이며 자동 발주는 없습니다.

## 2. 관리자 메뉴

| 그룹 | 화면 |
| --- | --- |
| 대시보드 | 운영 현황 `/admin`, 통합 대시보드 `/admin/operations` |
| 상품관리 | 상품 목록, 상품 등록(AI) `/admin/products/new` |
| 매입관리 | 매입장부 `/admin/purchases`, 매입 등록, AI 영수증 분석 `/admin/purchases/ai`, 상품 매칭, 월별 현황, 증빙 관리, 거래처 |
| 재고관리 | 재고 현황, 입고 관리 `/admin/inventory/receipts`, 재고 수불부, 부족·장기 재고 |
| 판매분석 | 상품별 손익 `/admin/analytics`, 월별 손익, 가격 정책 `/admin/pricing` |
| AI 운영관리 | 분석 이력·사용량 `/admin/ai/operations`, AI 발주 추천 |

## 3. 환경변수

API 키는 `.env` 또는 서버 환경변수에만 둡니다. Git·DB·프론트엔드에 넣지 않습니다.

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `OPENAI_API_KEY` | (없음) | 없으면 AI 화면은 "사용 불가"로 표시되고 직접 입력은 그대로 동작 |
| `OPENAI_MODEL` | (없음) | 이미지 입력을 지원하는 모델 |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | |
| `AI_RECEIPT_ENABLED` / `AI_PRODUCT_ENABLED` | `true` | 기능별 on/off |
| `AI_MOCK_ENABLED` | `false` | `true`면 OpenAI를 호출하지 않고 `[MOCK]` 표시된 테스트 응답 반환(테스트 전용) |
| `AI_TIMEOUT_SECONDS`, `AI_MAX_RETRIES`, `AI_CACHE_DAYS` | 90, 2, 30 | 타임아웃·재시도·동일 파일 캐시 기간 |
| `AI_INPUT_PRICE_PER_MILLION`, `AI_OUTPUT_PRICE_PER_MILLION` | 0.40, 1.60 | 예상 비용 표시용(USD / 100만 토큰) |
| `PRIVATE_UPLOAD_DIR` | `../private-uploads` (Docker: `/app/private-uploads`) | 증빙 원본 비공개 저장소. `/uploads`로 공개되지 않음 |
| `MULTIPART_MAX_FILE_SIZE` / `MULTIPART_MAX_REQUEST_SIZE` | 10MB / 60MB | 업로드 한도 |

Docker 배포 시 `shop_ai_private_uploads` 볼륨이 `/app/private-uploads`에 연결됩니다. 증빙 파일 백업 대상에 포함하세요.

## 4. 보안

- 업로드 파일은 확장자와 실제 내용(매직 바이트)을 모두 검사합니다(JPG·PNG·WEBP·PDF). 파일 수·크기 제한이 있습니다.
- 증빙은 비공개 경로에 저장되고 관리자 API(`/api/admin/purchases/evidence/{id}/file`)로만 내려받습니다. 응답에 `Content-Security-Policy: default-src 'none'`, `X-Content-Type-Options: nosniff`가 붙습니다.
- 영수증 안의 문구는 데이터로만 취급하며 명령으로 해석하지 않습니다.
- 매입장부·증빙·입고·가격 정책 변경은 감사 로그에 남습니다. AI 이력에는 원본 이미지·개인정보를 저장하지 않습니다.

## 5. 계산 규칙

- 관리번호: `YYYYMM-001` (월별 순번. `purchase_ledger_sequence`를 `INSERT … ON CONFLICT DO UPDATE`로 원자적으로 증가시켜 동시 등록에도 중복 없음)
- 차액 = 매입액 − 지급액
- 입고 원가(개당) = 매입 단가 + 부대비용 배분액 ÷ 수량. 배분 기준은 수량 비율 또는 금액 비율
- 원가 방식: 이동평균(가중평균) / 최근 매입원가 / 표준원가(상품 매입가)
- 목표 판매가 = 원가 ÷ (1 − 목표 마진율). 기본 40%(경쟁, 내부 목표) · 50%(표준) · 55%(고마진), 최소 마진 40%
- 끝자리 규칙(기본): 900원 끝자리, x8,900원 이상은 x9,000원 (37,200→37,900, 42,100→42,900, 48,300→49,000)
- 순매출 = 총판매 − 할인 − 환불, 매출총이익 = 순매출 − 매출원가
- 공헌이익 = 매출총이익 + 배송비 수입 − 결제수수료 − 배송비 − 기타 판매비. 고정비 데이터가 없어 영업이익은 계산하지 않습니다.
- 판매 시점 원가는 `order_item.unit_cost`에 스냅샷으로 저장되며, 스냅샷이 없는 과거 주문은 현재 원가로 추정하고 화면에 표시합니다.

## 6. 주요 API

| 기능 | 메서드 · 경로 |
| --- | --- |
| 매입장부 목록/등록 | `GET/POST /api/admin/purchases` |
| 매입장부 상세/수정/삭제 | `GET/PUT/DELETE /api/admin/purchases/{id}` (삭제는 소프트 삭제, 입고 확정 후 불가) |
| 월별 요약 | `GET /api/admin/purchases/summary?year=` |
| 엑셀 내보내기 | `GET /api/admin/purchases/export?scope=all\|year\|month\|supplier\|search` |
| 중복 검사 | `POST /api/admin/purchases/duplicates-check` |
| 증빙 업로드/목록/파일/수정/삭제 | `POST/GET /api/admin/purchases/evidence`, `GET .../evidence/{id}/file`, `PATCH/DELETE .../evidence/{id}` |
| AI 영수증 분석 | `POST /api/admin/purchases/ai/analyze` (multipart `files`, `forceRefresh`) |
| 상품 매칭 | `POST /api/admin/purchases/items/match`, `GET .../items/unmatched`, `GET .../items/{itemId}/candidates` |
| 거래처 | `GET/POST /api/admin/suppliers`, `GET/PUT .../{id}`, `GET .../{id}/mappings`, `DELETE .../{id}/mappings/{mappingId}` |
| 입고 | `GET/POST /api/admin/inventory/receipts`, `GET .../{id}`, `POST .../{id}/confirm`, `POST .../{id}/cancel` |
| 재고 | `GET /api/admin/inventory/status`, `/transactions`, `/low-stock`, `/slow-moving` |
| 가격 | `GET/PUT /api/admin/pricing/policies`, `POST /api/admin/pricing/recommend` |
| 원가·가격 이력 | `GET /api/admin/products/{productId}/cost-history`, `/cost-summary`, `/price-history` |
| 손익 | `GET /api/admin/analytics/product-profit`, `/monthly-profit`, `/category-sales` |
| 통합 대시보드 | `GET /api/admin/dashboard/operations` |
| AI 상품 분석 | `POST /api/admin/products/ai/analyze`, `POST /api/admin/products/ai/prepare` |
| AI 운영 | `GET /api/admin/ai/analysis-status`, `/analyses`, `/usage`, `GET/POST /api/admin/ai/purchase-recommendations`, `POST .../{id}/decision` |

## 7. DB 마이그레이션과 롤백

- `V13__purchase_ledger_ai.sql`: 매입장부, 품목, 증빙, 관리번호 순번, AI 분석 이력, 상품 검색키워드·SEO 설명·매입가
- `V14__purchase_inventory_cost_pricing.sql`: 거래처·매핑, 입고·입고 품목, 재고 이동 확장(변경 전/후 수량), SKU 원가·원가 이력, 가격 정책·가격 이력, 주문 품목 원가 스냅샷, AI 발주 추천

두 마이그레이션은 기존 테이블에 컬럼·인덱스만 추가하며 기존 데이터를 지우지 않습니다.
되돌려야 할 때는 백업 후 `docs/db-rollback/V14__rollback.sql` → `V13__rollback.sql` 순서로 수동 실행합니다(새 테이블의 데이터는 삭제되며, 이미 입고 확정으로 늘어난 재고 수량은 되돌리지 않습니다).

## 8. 테스트

- 백엔드: 단위 테스트(가격 계산, 끝자리 규칙, 원가 배분, 파일 검증, 로컬 가짜 OpenAI 서버를 상대로 한 `OpenAiResponsesClientTest` 등)와 실제 PostgreSQL IT
  (`PurchaseInventoryFlowIT`: 매입→매칭→입고→중복 확정 방지→원가→판매 손익, `AiMockModeIT`: Mock 모드 분석·캐시·초안 저장)
  - IT 실행: `RUN_LOCAL_DB_IT=true`와 테스트 전용 DB(`DB_URL` 등)를 지정. 운영 DB에서 실행하지 마세요.
- 프론트엔드: `npx vitest run`, `npx next lint`, `tsc --noEmit`, `npx next build`

## 9. 검증 범위

- 실제 OpenAI 호출은 API 키가 없는 환경에서 개발되어 **미검증**입니다. 호출 코드와 응답 파싱은 로컬 가짜 OpenAI 서버 단위 테스트와 Mock 모드로만 확인했습니다.
- 상품 매칭과 발주 추천 수량, 가격 계산은 규칙 기반이며 AI는 설명 문구만 작성합니다.
