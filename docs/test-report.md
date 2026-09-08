# Test report

검증 정책: Phase 종료 시 변경 기능 관련 테스트·lint만. 전체 `mvn test` / `npm run build` / Playwright는 최종(Phase 8). 동일 오류 자동수정 최대 2회. 외부 유료 API는 Mock.

| Check | Result | Notes |
|-------|--------|-------|
| Phase 0 docs | 성공 | |
| Phase 1 SchemaValidationLocalIT | 성공 | |
| SchemaValidationIT (Testcontainers) | 스킵 | Docker 29 npipe 호환 — Local IT로 대체 |
| Phase 2 auth | 성공 | 15 |
| Phase 3 catalog | 성공 | 8 |
| Phase 4 inventory/cart/wishlist | 성공 | 11 |
| Phase 5 order/payment | 성공 | 8 |
| Phase 6 review/AI | 성공 | 12 |
| Phase 7 admin | 성공 | 3 |
| **mvn test (전체, RUN_LOCAL_DB_IT=true)** | **성공** | **59 run, 0 fail, 1 skipped** |
| **npm run lint** | **성공** | |
| **npm run test** | **성공** | **9 tests** |
| **npm run build** | **성공** | Next.js 15.2.4 |
| Playwright | 미검증 | 미설치 — 핵심 흐름은 API IT로 커버 |
| docker compose postgres | 성공 | healthy |
| docker compose full stack | 미검증 | postgres만 상시 검증 |

## Mock 범위
- 결제: MockPaymentGateway만
- AI: RuleBased 기본, OpenAI Key 없으면 호출 없음

## 알려진 제한
- Testcontainers PostgreSQL IT는 환경 이슈로 스킵 (`SchemaValidationLocalIT` 사용)
- Playwright E2E 미구성
- 관리자 계정은 회원가입 후 DB에서 role=ADMIN 부여
