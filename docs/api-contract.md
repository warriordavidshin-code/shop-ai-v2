# BoutiqueCamel AI Shop v2 — API Contract

Base path: `/api`  
성공/실패 응답 형식을 일관되게 유지한다.

## Common error

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "입력값을 확인해 주세요.",
  "fieldErrors": {
    "email": "올바른 이메일 형식이 아닙니다."
  },
  "requestId": "uuid"
}
```

공통 코드: `VALIDATION_FAILED`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `BUSINESS_RULE_VIOLATION`, `INTERNAL_ERROR`

## Enums

| Enum | Values |
|------|--------|
| Gender | `FEMALE`, `MALE`, `OTHER`, `PREFER_NOT_TO_SAY` |
| MemberRole | `CUSTOMER`, `ADMIN` |
| MemberStatus | `ACTIVE`, `DORMANT`, `BLOCKED`, `WITHDRAWN` |
| ProductStatus | `DRAFT`, `ON_SALE`, `SOLD_OUT`, `HIDDEN`, `DISCONTINUED` |
| ImageType | `MAIN`, `DETAIL`, `MODEL`, `COLOR`, `SIZE` |
| MovementType | `RECEIPT`, `SALE`, `CANCEL`, `RETURN`, `ADJUSTMENT`, `RESERVATION`, `RELEASE` |
| OrderStatus | `CREATED`, `PAYMENT_PENDING`, `PAID`, `PREPARING`, `SHIPPED`, `DELIVERED`, `CANCELLED` |
| PaymentStatus | `READY`, `APPROVED`, `FAILED`, `CANCELLED` |
| FitRating | `SMALL`, `TRUE_TO_SIZE`, `LARGE` |
| ReviewStatus | `VISIBLE`, `HIDDEN` |
| Sort | `RECOMMENDED`, `NEWEST`, `PRICE_ASC`, `PRICE_DESC` |

---

## 1. Auth & Member

### POST `/api/auth/signup`

Request:

```json
{
  "loginId": "cameluser",
  "email": "user@example.com",
  "password": "StrongPassword1!",
  "name": "홍길동",
  "birthDate": "1990-01-01",
  "gender": "FEMALE",
  "phone": "01012345678",
  "postcode": "30100",
  "address1": "세종특별자치시 ...",
  "address2": "101동 101호",
  "termsAgreed": true,
  "privacyAgreed": true
}
```

Validation: loginId unique (영문 시작 4–20자); email optional unique+format; password ≥10 with letter/digit/special; name 2–100; birthDate not future; under-14 requires guardian policy (reject with guidance); terms/privacy required.

Response `201`: `MemberResponse` + Set-Cookie (access/refresh)

### POST `/api/auth/login`

Request: `{ "loginId", "password" }` (이메일도 loginId에 넣으면 기존 계정 로그인 가능)  
Response `200`: `MemberResponse` + cookies  
실패 메시지는 계정 존재 여부와 무관하게 동일.

### POST `/api/auth/refresh`

Refresh cookie로 access 재발급 + refresh 회전.  
Response `200` + new cookies

### POST `/api/auth/logout`

Refresh 폐기 + cookie 만료. Response `204`

### GET `/api/members/me`

Response `200`:

```json
{
  "memberId": 1,
  "loginId": "cameluser",
  "email": "user@example.com",
  "name": "홍길동",
  "birthDate": "1990-01-01",
  "age": 36,
  "gender": "FEMALE",
  "phone": "01012345678",
  "postcode": "30100",
  "address1": "...",
  "address2": "...",
  "role": "CUSTOMER"
}
```

`age`는 Clock 기준 만 나이 (고정값 테스트 금지).

### PATCH `/api/members/me`

Request (부분): `name`, `birthDate`, `gender`, `phone`, `postcode`, `address1`, `address2`  
아이디/이메일 변경은 읽기 전용 (본인확인 없음).

### PATCH `/api/members/me/password`

Request: `{ "currentPassword", "newPassword" }`

---

## 2. Catalog

### GET `/api/categories`

Response: category tree (`categoryId`, `name`, `slug`, `children[]`)

### GET `/api/products`

Query: `page`, `size`, `category`, `keyword`, `sort`, `minPrice`, `maxPrice`, `color`, `size`, `availableOnly`

Response `PageResponse<ProductSummary>`:

```json
{
  "content": [{
    "productId": 1,
    "productName": "...",
    "brandName": "BoutiqueCamel",
    "normalPrice": 59000,
    "salePrice": 49000,
    "discountRate": 17,
    "mainImageUrl": null,
    "colorCount": 3,
    "soldOut": false,
    "wishlisted": false
  }],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

### GET `/api/products/{productId}`

상세: images, skus(+availableQuantity), measurements, description, fit/material meta

### GET `/api/products/new` · `/api/products/best`

신상품·베스트 목록 (요약 DTO)

---

## 3. Cart & Wishlist

### GET `/api/cart`

### POST `/api/cart/items` — `{ "skuId", "quantity" }`

### PATCH `/api/cart/items/{cartItemId}` — `{ "quantity" }`

### DELETE `/api/cart/items/{cartItemId}`

### POST `/api/cart/merge` — 비회원 임시 장바구니 병합 `{ "items": [{ "skuId", "quantity" }] }`

### GET `/api/wishlist`

### POST `/api/wishlist/{productId}`

### DELETE `/api/wishlist/{productId}`

---

## 4. Order & Payment

### POST `/api/orders`

Header: `Idempotency-Key` (required)  
Request:

```json
{
  "items": [{ "skuId": 1, "quantity": 2 }],
  "receiverName": "...",
  "receiverPhone": "...",
  "postcode": "...",
  "address1": "...",
  "address2": "...",
  "orderMemo": "..."
}
```

서버가 가격·배송비·재고 재검증. Response: order summary + `orderNo`

### GET `/api/orders/{orderNo}`

### GET `/api/members/me/orders?page&size`

### POST `/api/payments/mock/approve` — `{ "orderNo" }`

### POST `/api/orders/{orderNo}/cancel`

---

## 5. Reviews

### GET `/api/products/{productId}/reviews?page&size`

### POST `/api/products/{productId}/reviews`

구매 완료 `orderItemId` 필수. rating 1–5, content, optional height/weight, purchasedSize, fitRating

### PATCH `/api/reviews/{reviewId}`

### DELETE `/api/reviews/{reviewId}`

---

## 6. AI

### POST `/api/ai/size-recommendations`

Request: productId + body measurements / preferred fit  
Response: recommended size, confidence (`HIGH`/`MEDIUM`/`LOW`/`UNCERTAIN`), reasons[]

### POST `/api/ai/outfit-recommendations`

Request: occasion, style, colors[], budget  
Response: recommendationId, outfits[{ items: ProductSummary[] }], provider

### POST `/api/ai/recommendations/{recommendationId}/events`

`{ "eventType": "VIEW"|"CLICK"|"CART"|"PURCHASE"|"SKIP", "productId"? }`

---

## 7. Admin (`ADMIN` only)

| Method | Path |
|--------|------|
| GET | `/api/admin/dashboard` |
| GET/POST | `/api/admin/products` |
| GET | `/api/admin/products/{productId}` |
| PUT | `/api/admin/products/{productId}` |
| PATCH | `/api/admin/products/{productId}/status` |
| POST | `/api/admin/uploads` (multipart `file`) |
| GET/POST | `/api/admin/hero-banners` |
| PUT/DELETE | `/api/admin/hero-banners/{bannerId}` |
| GET | `/api/hero-banners` (active only, public) |
| PATCH | `/api/admin/inventories/{skuId}/adjust` |
| GET | `/api/admin/inventory-movements` |
| GET | `/api/admin/orders` |
| PATCH | `/api/admin/orders/{orderNo}/status` |
| GET | `/api/admin/members` |
| PATCH | `/api/admin/members/{memberId}/status` |
| PATCH | `/api/admin/reviews/{reviewId}/status` |
| GET | `/api/admin/ai` |

재고 조정: `{ "quantityDelta", "reason" }` — movement `ADJUSTMENT` 기록
