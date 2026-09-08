# BoutiqueCamel AI Shop v2

여성·아동 의류 쇼핑몰 MVP (쁘띠카멜).

## Quick start

```bash
# DB only
docker compose up -d postgres

# Backend
cd shop-backend
mvn spring-boot:run

# Frontend
cd shop-frontend
npm install
npm run dev
```

- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html

Full stack:

```bash
docker compose up --build
```

## Vercel (frontend)

GitHub 저장소 루트에는 Next.js가 없고 `shop-frontend`에 있습니다.

1. Vercel에서 이 저장소를 Import
2. **Root Directory**를 `shop-frontend`로 설정
3. Environment Variables 예시:
   - `BACKEND_URL` / `NEXT_PUBLIC_BACKEND_URL` — 백엔드 API 주소 (예: `https://api.example.com`)

Root Directory를 비우면 배포 URL이 `NOT_FOUND` 404가 납니다.

## Local DB

| Key | Value |
|-----|-------|
| JDBC | `jdbc:postgresql://localhost:5432/shop_ai` |
| User | `shop_ai` |
| Password | `shop_ai123!` |

통합 테스트: `RUN_LOCAL_DB_IT=true mvn test`

## 테스트 계정

관리자 SQL 평문 비밀번호는 넣지 않습니다. 회원가입 API/화면으로 고객 계정을 만든 뒤, DB에서 role을 `ADMIN`으로 변경하세요.

```sql
UPDATE member SET role = 'ADMIN' WHERE email = 'your@email.com';
```

고객: `/signup` → 로그인

## Mock / 외부 API

- **결제**: MockPaymentGateway만 사용. UI에 "개발용 Mock 결제" 표시.
- **AI**: 기본 `AI_PROVIDER=rule`. OpenAI Key 없어도 실행. Key가 있을 때만 선택적 OpenAI Provider.
- 문자/실제 PG 호출 없음.

## Logging

백엔드(Log4j2) 로그 파일:

| 파일 | 설명 |
|------|------|
| `log/shop-backend.log` | 현재 로그 |
| `log/shop-backend-yyyy-MM-dd.log` | 일별 롤링 |
| `log/shop-backend-error.log` | ERROR 이상 |
| `log/shop-backend-error-yyyy-MM-dd.log` | ERROR 일별 롤링 |

경로 기본값: `shop-backend` 실행 시 `../log` (= `shop-ai-v2/log`).  
환경변수 `LOG_DIR`로 변경 가능.

## Docs

- `docs/architecture.md`
- `docs/api-contract.md`
- `docs/security.md`
- `docs/test-report.md`
