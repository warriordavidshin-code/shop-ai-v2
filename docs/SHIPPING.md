# BoutiqueCamel 배송 시스템 (집하 · 송장 · 실시간 배송조회 · 반품 회수)

## 흐름

- 배송: 주문완료 → 결제완료 → 상품준비중 → 배송준비 → 집하요청 → 집하완료 → 배송중 → 배송출발 → 배송완료
- 반품: 반품신청 → 반품승인 → 반품수거 요청 → 기사 방문수거 → 반품배송중 → 반품입고 → 환불처리

| ShipmentStatus | 표시 | 방향 |
| --- | --- | --- |
| PREPARING / READY / PICKUP_REQUESTED | 상품준비중 / 배송준비 / 집하요청 | 배송 |
| PICKED_UP / IN_TRANSIT / OUT_FOR_DELIVERY / DELIVERED | 집하완료 / 배송중 / 배송출발 / 배송완료 | 배송 |
| RETURN_REQUESTED / RETURN_PICKUP_REQUESTED / RETURN_IN_TRANSIT / RETURN_COMPLETED | 반품요청 / 반품수거요청 / 반품배송중 / 반품입고 | 반품 |
| CANCELLED | 배송취소 | - |

상태는 앞 단계로만 이동합니다(택배사 조회 결과가 늦게 와도 뒤로 가지 않음). 배송 상태가 바뀌면 주문 상태도 함께 맞춰집니다
(집하완료 이후 → `SHIPPED`, 배송완료 → `DELIVERED`, 반품 → `RETURN_REQUESTED` / `RETURNED`).

## 1. 신규 / 수정 파일

### Backend (`shop-backend/src/main/java/com/petitcamel/shop`)

| 경로 | 내용 |
| --- | --- |
| `shipping/domain/*` | `Shipment`, `ShipmentTrackingEvent`, `ReturnRequest`, `ShippingPolicy`, `ShippingExtraArea`, `DeliveryCompany(Code)`, 상태 enum |
| `shipping/repository/*` | JPA 리포지토리 |
| `shipping/provider/ShippingProvider` | 배송조회 연동 인터페이스 |
| `shipping/provider/sweettracker/SweetTrackerShippingProvider` | 스마트택배(스윗트래커) 구현 (1차) |
| `shipping/provider/goodsflow/GoodsflowShippingProvider` | 굿스플로 자리(미구현, 설정 시에도 MANUAL 로 폴백) |
| `shipping/provider/manual/ManualShippingProvider` | API 키가 없을 때의 수동 모드 |
| `shipping/provider/ShippingProviderRegistry`, `TrackingStatusMapper` | 사용 연동사 선택, 택배사 상태 → ShipmentStatus 매핑 |
| `shipping/pickup/*`, `shipping/waybill/*` | 집하요청 / 운송장 발급·출력 인터페이스 (현재 수동 / 미지원 구현) |
| `shipping/service/ShipmentService` | 송장 등록·수정, 일괄 등록, 상태 변경, 주문 상태 동기화 |
| `shipping/service/TrackingService`, `ShipmentTrackingScheduler` | 배송조회(캐시 25분), 30분 주기 자동 조회 |
| `shipping/service/ReturnService` | 반품 신청 · 승인 · 거절 · 수거 · 입고 · 환불(재입고) |
| `shipping/service/ShippingFeeService`, `ShippingFeeCalculator` | 배송비 정책, 제주/도서산간 추가 배송비 |
| `shipping/service/DeliveryCompanyService`, `ShipmentViewAssembler`, `ShipmentOrderSyncListener` | 택배사 코드, 응답 조립, 주문 상태 변경 연동 |
| `shipping/event/*` | `ShipmentStatusChangedEvent` → 알림("상품이 발송되었습니다." / "상품 배송이 완료되었습니다.") |
| `shipping/support/ShippingLogMasker` | 로그용 송장번호 마스킹(`1234********`) |
| `shipping/controller/*` | 고객 / 관리자 REST API |
| `shipping/config/*` | `shipping.*` 설정 바인딩 |
| `order/event/OrderStatusChangedEvent` | 주문 상태 변경 이벤트 |
| `admin/service/AdminOrderQueryService`, `admin/dto/AdminOrderDetailResponse` | 관리자 주문 목록(배송 컬럼·필터)과 상세 |
| 수정: `admin/controller/AdminOrderController`, `admin/dto/AdminOrderSummaryResponse`, `AdminDashboardResponse`, `AdminDashboardService` | 배송 컬럼, 대시보드 배송 카드 |
| 수정: `order/domain/OrderStatus` | `RETURN_REQUESTED`, `RETURNED` 추가 |
| 수정: `order/service/OrderService`, `OrderCancelRequestService`, `cart/service/CartService` | DB 배송비 정책 사용, 상태 변경 이벤트 발행 |
| 수정: `payment/domain/Payment` | `refunded_amount` |
| 수정: `security/SecurityConfig` | `/api/shipping/policy`, `/api/shipping/quote` 공개 |
| 삭제: `common/util/DeliveryFeePolicy` | `ShippingFeeService` 로 대체 |
| `resources/db/migration/V11__shipping_returns.sql` | 신규 테이블 |
| `resources/application.yml`, `test/resources/application-test.yml` | `shipping.*` 설정 |

테스트: `shipping/ShippingIntegrationIT`(10), `ShippingFeeCalculatorTest`, `TrackingStatusMapperTest`,
`SweetTrackerShippingProviderTest`, `ShipmentStatusTest`, `ShippingLogMaskerTest`, `ShipmentNotificationListenerTest`.

### Frontend (`shop-frontend/src`)

| 경로 | 내용 |
| --- | --- |
| `features/shipping/api.ts`, `server.ts`, `useShippingPolicy.ts` | 배송 정책·견적, 배송조회, 반품 API |
| `features/shipping/progress.ts`, `csv.ts` | 진행 단계 계산, 송장 CSV 파서 |
| `features/admin/shipping.ts`, `guard.ts` | 관리자 배송·반품·설정 API, 관리자 페이지 권한 확인 |
| `components/orders/ShipmentTracker.tsx` | 마이페이지 배송 진행바 + 배송조회 모달 |
| `components/orders/ReturnPanel.tsx` | 반품 신청 / 진행 현황 |
| `components/admin/AdminOrdersClient.tsx`, `BulkInvoicePanel.tsx` | 관리자 주문·배송 목록, 체크박스/CSV 일괄 송장 등록 |
| `components/admin/AdminOrderDetailClient.tsx` | 주문 상세 송장 등록, 상태 변경, 집하요청, 운송장 |
| `components/admin/ReturnsClient.tsx` | 반품 관리 |
| `components/admin/ShippingSettingsClient.tsx` | 배송비 정책, 도서산간 지역, 택배사, 연동 상태 |
| `app/admin/orders/page.tsx`, `app/admin/orders/[orderNo]/page.tsx`, `app/admin/returns/page.tsx`, `app/admin/shipping-settings/page.tsx` | 관리자 페이지 |
| 수정: `app/admin/page.tsx`, `components/admin/AdminNav.tsx` | 배송 대시보드 카드, 메뉴 |
| 수정: `app/(shop)/mypage/orders/*`, `components/cart/CartClient.tsx`, `components/orders/CheckoutClient.tsx`, `DeliveryFee.tsx`, `OrderCancelPanel.tsx`, `features/orders/delivery.ts`, `status.ts` | 배송비 "무료" 표시, 정책 연동, 반품 상태 라벨 |

설정: `docker-compose.yml`, `.env.example` 에 `SHIPPING_*` 추가.

## 2. DB (Flyway `V11__shipping_returns.sql`)

앱 기동 시 Flyway 가 자동 적용합니다. 수동 적용이 필요하면 해당 파일을 그대로 실행하면 됩니다.

| 테이블 | 용도 |
| --- | --- |
| `delivery_company` | 내부 택배사 코드(CJ, HANJIN, LOTTE, POST, LOGEN, ETC), 이름, 조회 URL 템플릿, 사용 여부 |
| `delivery_company_code` | 연동사별 택배사 코드 (예: SWEETTRACKER CJ=04, 한진=05, 롯데=08, 우체국=01, 로젠=06) |
| `shipment` | 배송/반품 회수 1건 = 1행. `delivery_company`, `tracking_number`, `shipment_status`, `pickup_requested_at`, `picked_up_at`, `shipped_at`, `delivered_at`, `last_tracking_checked_at`, `last_tracking_error` |
| `shipment_tracking_event` | 배송 이력 (`INTERNAL` = 관리자 처리, `PROVIDER` = 택배사 조회 결과) |
| `return_request` | 반품 요청 (사유, 수거지, 회수 송장, 반품 배송비, 환불금액, 재입고 여부, 처리 시각) |
| `shipping_policy` | 단일 행 배송비 정책 (기본 3,000 / 무료 기준 50,000 / 제주 +3,000 / 도서산간 +5,000 / 반품 3,000) |
| `shipping_extra_area` | 제주·도서산간 우편번호 범위 |
| 변경 | `orders.order_status` 체크에 `RETURN_REQUESTED`, `RETURNED` 추가, `payment.refunded_amount` 추가 |

## 3. REST API

### 고객 (로그인 필요, 본인 주문만)

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/orders/{orderId}/tracking?type=DELIVERY\|RETURN` | 배송조회 (캐시 25분, 이력 time 형식 `2026-09-29 13:10`) |
| GET | `/api/orders/{orderId}/return` | 반품 가능 여부, 사유 목록, 진행 중 반품 |
| POST | `/api/orders/{orderId}/return` | 반품 신청 `{returnReason, returnMemo, pickupName, pickupPhone, pickupPostcode, pickupAddress, pickupAddressDetail}` |
| GET | `/api/shipping/policy` | 배송비 정책 (공개) |
| GET | `/api/shipping/quote?amount=&postcode=` | 배송비 견적 (공개, 제주/도서산간 반영) |

반품 사유 `returnReason`: `SIZE_MISMATCH`(사이즈가 맞지 않음), `NOT_SATISFIED`(상품이 마음에 들지 않음), `DEFECTIVE`(상품 불량),
`WRONG_DELIVERY`(오배송), `CHANGE_OF_MIND`(단순 변심), `OTHER`(기타). 불량·오배송은 반품 배송비 무료.

배송조회 응답 예:

```json
{
  "orderId": 12, "orderNo": "20260929-000012", "shipmentType": "DELIVERY",
  "deliveryCompany": "CJ", "deliveryCompanyName": "CJ대한통운", "trackingNumber": "123456789012",
  "status": "IN_TRANSIT", "statusName": "배송중", "externalTracking": true, "message": null,
  "events": [{ "time": "2026-09-29 13:10", "location": "서울 강남", "description": "집하", "status": "PICKED_UP" }]
}
```

외부 API 장애 시 HTTP 200 에 `message: "배송정보를 일시적으로 조회할 수 없습니다. 잠시 후 다시 확인해주세요."` 와
마지막으로 저장된 이력을 돌려줍니다(페이지가 깨지지 않음).

### 관리자 (ADMIN)

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/admin/orders?view=ALL\|TODAY\|READY\|SHIPPING\|DELIVERED\|RETURN\|CANCEL&keyword=&page=&size=` | 주문·배송 목록 |
| GET | `/api/admin/orders/{orderNo}` | 주문 상세 + 배송 / 반품 |
| PUT | `/api/admin/orders/{orderId}/shipment` | 송장 등록/수정 `{deliveryCompany, trackingNumber}` → `{success, message:"송장번호가 등록되었습니다.", shipment}` |
| PATCH | `/api/admin/orders/{orderId}/shipment/status` | 배송 상태 수동 변경 `{status}` |
| POST | `/api/admin/orders/{orderId}/shipment/pickup-request` | 집하요청 `{deliveryCompany?}` (현재 수동 기록) |
| POST | `/api/admin/orders/{orderId}/shipment/refresh?type=` | 배송조회 즉시 갱신 (60초 간격 제한) |
| POST | `/api/admin/orders/{orderId}/shipment/waybill` / `.../waybill/print` | 운송장 발급/출력 (연동 전: 400 안내) |
| POST | `/api/admin/shipments/bulk` | 일괄 송장 `{items:[{orderNumber, deliveryCompany, trackingNumber}]}` (최대 500건, 행별 결과) |
| GET | `/api/admin/returns?status=OPEN\|ALL\|REQUESTED…` | 반품 목록 |
| POST | `/api/admin/returns/{id}/approve` · `/reject {reason}` | 승인 / 거절 |
| POST | `/api/admin/returns/{id}/pickup-request {deliveryCompany?, trackingNumber?}` | [반품수거 요청] |
| PUT | `/api/admin/returns/{id}/tracking {deliveryCompany, trackingNumber}` | 회수 송장 등록 |
| PATCH | `/api/admin/returns/{id}/status {status: PICKED_UP\|IN_TRANSIT\|RECEIVED}` | 기사 방문수거 / 반품배송중 / 반품입고 |
| POST | `/api/admin/returns/{id}/refund {restock, adminMemo}` | 환불 처리 (+재입고) |
| GET/PUT | `/api/admin/shipping/policy` | 배송비 정책 |
| GET/POST/DELETE | `/api/admin/shipping/extra-areas[/{areaId}]` | 제주·도서산간 지역 |
| GET / PATCH | `/api/admin/shipping/delivery-companies[/{code}] {enabled}` | 택배사 목록 / 사용 여부 |
| GET | `/api/admin/shipping/integration` | 연동 상태 (키 값은 절대 반환하지 않음, 설정 여부만) |

## 4. 환경변수

API 키는 **절대 Git 에 커밋하지 않습니다.** 서버의 `.env`(Git 제외) 또는 OS 환경변수로만 설정합니다.

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `SHIPPING_PROVIDER` | `SWEETTRACKER` | `SWEETTRACKER` / `GOODSFLOW` / `MANUAL` |
| `SHIPPING_API_KEY` | (없음) | 연동사 API 키. 없으면 자동으로 MANUAL(관리자 수동 상태 관리) |
| `SHIPPING_CLIENT_ID` | (없음) | 굿스플로 등 클라이언트 ID 가 필요한 연동사용 |
| `SHIPPING_TRACKING_CACHE_MINUTES` | `25` | 고객 조회 캐시(분) |
| `SHIPPING_TRACKING_SCHEDULER_ENABLED` | `true` | 자동 조회 스케줄러 |
| `SHIPPING_TRACKING_CRON` | `0 */30 * * * *` | 자동 조회 주기 (배송중 상태만 조회) |
| `SHIPPING_TRACKING_FORCE_REFRESH_MIN_SECONDS` | `60` | 관리자 즉시 조회 최소 간격 |
| `SHIPPING_SWEETTRACKER_BASE_URL` | `https://info.sweettracker.co.kr` | 스마트택배 API 주소 |

Docker 배포: 서버의 `.env` 에 위 값을 넣고 `docker compose up -d backend` 로 재기동합니다(`docker-compose.yml` 이 전달).

## 5. 배송조회 API 연동 방식

```
TrackingService ──▶ ShippingProviderRegistry.active() ──▶ ShippingProvider
                                                          ├─ SweetTrackerShippingProvider (1차)
                                                          ├─ GoodsflowShippingProvider   (예정)
                                                          └─ ManualShippingProvider      (키 없음)
```

1. 관리자가 송장을 등록하면 배송이 `배송중`이 되고 고객에게 발송 알림 이벤트가 발행됩니다.
2. 고객이 배송조회를 열면 `last_tracking_checked_at` 이 25분 이상 지난 경우에만 연동사를 호출하고, 그 외에는 저장된 이력을 반환합니다.
3. 스케줄러는 30분마다 추적 가능한 상태(집하요청~배송출발, 반품 회수 중)만 최대 200건씩 조회합니다.
4. 연동사 응답의 택배사 상태는 `TrackingStatusMapper` 로 ShipmentStatus 에 매핑되고, 앞 단계로만 반영됩니다.
5. 택배사 코드는 DB(`delivery_company_code`)에서 연동사별로 변환합니다. 코드가 없으면 조회를 건너뛰고 수동 관리합니다.
6. 외부 호출은 DB 트랜잭션 밖에서 실행하며(타임아웃 3초/5초), 실패하면 `last_tracking_error` 만 남기고 화면은 저장된 이력으로 표시합니다.
7. 로그 형식: `[SHIPPING] provider=SWEETTRACKER orderId=18 company=CJ trackingNumber=5566******** status=IN_TRANSIT result=UPDATED`
   (API 키·고객 이름·전화·주소는 기록하지 않음).

## 6. 테스트 방법

```powershell
# 백엔드 단위 테스트
cd shop-backend
.\mvnw.cmd test

# 통합 테스트 (운영 DB 말고 임시 컨테이너 사용)
docker run -d --rm --name shopai-it-pg -e POSTGRES_DB=shop_ai -e POSTGRES_USER=shop_ai `
  -e POSTGRES_PASSWORD=it_test_pw -p 55432:5432 postgres:16-alpine -c max_connections=400
$env:RUN_LOCAL_DB_IT='true'; $env:DB_URL='jdbc:postgresql://localhost:55432/shop_ai'
$env:DB_USERNAME='shop_ai'; $env:DB_PASSWORD='it_test_pw'
.\mvnw.cmd verify -Dit.test=ShippingIntegrationIT
docker stop shopai-it-pg

# 프론트엔드
cd ..\shop-frontend
npx tsc --noEmit; npx vitest run; npx next lint; npx next build
```

`ShippingIntegrationIT` 는 가짜 연동사(FAKE)로 송장 등록 → 배송조회 → 캐시 → 즉시조회 → 배송완료, 외부 장애 안내문,
타인 주문 차단, 잘못된 입력, 일괄 등록, 반품 환불/재입고, 단순 변심 배송비 차감, 배송비 정책 변경을 검증합니다.

수동 확인: API 키 없이 기동하면 MANUAL 모드입니다. 관리자에서 송장을 등록하고 상태 버튼(집하완료 → 배송중 → 배송출발 → 배송완료)을
눌러 고객 마이페이지 진행바가 따라 움직이는지 확인합니다.

## 7. 관리자 사용법

- **대시보드**: 오늘 주문 / 배송 준비 / 배송중 / 배송완료 / 반품 요청 카드를 누르면 해당 목록으로 이동합니다.
- **주문 관리** (`/admin/orders`): 탭(전체·오늘·배송 준비·배송중·배송완료·반품·취소), 주문번호 검색. 목록에 고객명·상품명·금액·
  배송상태·택배사·송장번호·수거요청·배송조회·반품상태가 표시됩니다.
  - 일괄 등록: 주문을 체크 → 택배사 선택 → 송장번호 입력 → [선택 주문 송장 등록].
  - CSV/엑셀: `주문번호,택배사,송장번호` (헤더 선택, 택배사는 `CJ` 또는 `CJ대한통운`). 엑셀은 CSV 로 저장해 업로드 → 미리보기 → 등록 → 행별 결과.
- **주문 상세** (`/admin/orders/{주문번호}`): 택배사 선택 + 송장번호 → [송장 등록 · 배송 시작]. 상태 수동 변경, [집하 요청],
  [배송조회 새로고침], 운송장 발급/출력(연동 후), 처리 이력.
- **반품 관리** (`/admin/returns`): 승인 → [반품수거 요청](택배사에 회수 접수 후 기록, 회수 송장은 나중에 등록 가능) →
  기사 방문수거 → 반품배송중 → 반품입고 → [환불 처리](검수 통과 시 재고 복원 체크). 신청/승인/수거요청 단계에서 거절 가능.
- **배송 설정** (`/admin/shipping-settings`): 배송비 정책 수정, 제주·도서산간 우편번호 범위, 사용 택배사, API 연동 상태.

## 8. 고객 배송조회 사용법

- 마이페이지 → 주문내역 → 주문 상세에서 진행바(상품준비 → 집하완료 → 배송중 → 배송출발 → 배송완료)의 현재 단계가 강조됩니다.
- [배송조회]를 누르면 택배사, 송장번호, 현재 상태, 배송 이력(최신순)과 택배사 사이트 링크가 모달로 표시됩니다.
- 배송완료 후 같은 화면에서 [반품 신청]: 사유 선택, 상세 사유, 수거지 이름·연락처·주소 입력. 예상 환불금액과
  "검수 후 환불" 안내가 표시되고, 신청 후에는 반품 진행 단계와 회수 배송조회를 볼 수 있습니다.
- 장바구니 / 주문서의 배송비는 무료일 때 "무료"로 표시되고, 제주·도서산간은 추가 배송비가 안내됩니다.

## 9. 실제 택배사 API 연동 시 수정 위치

| 작업 | 위치 |
| --- | --- |
| 스윗트래커 키 발급 후 사용 | 코드 수정 없음: `SHIPPING_API_KEY` 설정 후 재기동. 응답 필드가 다르면 `SweetTrackerShippingProvider.tracking` / `parseTime` |
| 택배사 상태 문구 매핑 보완 | `shipping/provider/TrackingStatusMapper` |
| 굿스플로 / 택배사 직접 연동 | `ShippingProvider` 구현체 추가(예: `GoodsflowShippingProvider.tracking`), `isConfigured()` 에서 키 확인, `SHIPPING_PROVIDER` 변경 |
| 연동사별 택배사 코드 추가 | `delivery_company_code` 테이블에 `(company_code, provider, provider_code)` INSERT (새 Flyway 마이그레이션) |
| 새 택배사 추가 | `delivery_company` INSERT (조회 URL 템플릿의 `{trackingNumber}` 치환) |
| 자동 집하요청 / 반품 회수 접수 | `shipping/pickup/PickupService` 구현체 추가 (`ManualPickupService` 대체) |
| 운송장 발급 / 출력 | `shipping/waybill/WaybillService` 구현체 추가 (`UnsupportedWaybillService` 대체) |
| 발송 / 배송완료 알림(카카오 알림톡·메일) | `shipping/event/ShipmentNotifier` 구현체 추가 (`LoggingShipmentNotifier` 대체) |
| 조회 주기 / 캐시 | 환경변수 `SHIPPING_TRACKING_CRON`, `SHIPPING_TRACKING_CACHE_MINUTES` |
