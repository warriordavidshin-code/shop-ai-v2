# Social login setup (Kakao / Naver)

BoutiqueCamel keeps the existing email/password JWT cookie auth and adds Kakao + Naver OAuth
as additional sign-up/login paths. Provider tokens never reach the browser.

Production shop domain: **https://btc-camel.com**

## Flow

1. Browser opens `GET /api/shop/auth/{kakao|naver}/login` (Next.js BFF → backend)
2. Backend stores a one-time `state`, redirects to the provider authorize URL
3. Provider redirects to `{OAUTH_PUBLIC_CALLBACK_BASE}/auth/{kakao|naver}/callback`
4. Backend validates `state`, exchanges `code` server-side, **finds or auto-creates** `member` by
   `(auth_provider, provider_user_id)`, issues the same JWT + refresh cookies, redirects to the shop UI.
   First-time Kakao users are registered automatically and logged in in the same callback.

Use the **frontend BFF URL** as `OAUTH_PUBLIC_CALLBACK_BASE` so `Set-Cookie` attaches to the shop origin:

```text
OAUTH_PUBLIC_CALLBACK_BASE=https://btc-camel.com/api/shop
FRONTEND_URL=https://btc-camel.com
```

Local development override:

```text
OAUTH_PUBLIC_CALLBACK_BASE=http://localhost:3000/api/shop
FRONTEND_URL=http://localhost:3000
```

## DB (Flyway V9)

- `member.auth_provider` (`LOCAL` | `KAKAO` | `NAVER`)
- `member.provider_user_id`
- `member.profile_image_url`
- `password_hash` nullable (social accounts)
- profile fields `birth_date`, `gender`, `phone`, `postcode`, `address1` nullable for incomplete social profiles
- unique index on `(auth_provider, provider_user_id)` where `provider_user_id IS NOT NULL`

Email collision with an existing account does **not** auto-link; Kakao signup fails with a conflict message.

## Kakao developers

1. Create an app at [Kakao Developers](https://developers.kakao.com/)
2. Enable Kakao Login
3. Redirect URI (production):

```text
https://btc-camel.com/api/shop/auth/kakao/callback
```

4. Copy REST API key → `KAKAO_CLIENT_ID`
5. Create client secret → `KAKAO_CLIENT_SECRET`
6. Set `KAKAO_ENABLED=true`
7. Kakao Login 동의 항목을 **필수 동의**로 설정하고 앱에서 아래 scope를 요청합니다.

| 항목 | scope / 필드 | 저장 위치 |
|---|---|---|
| 카카오계정(이메일) | `account_email` | `member.email` |
| 이름 | `name` | `member.name` |
| 성별 | `gender` | `member.gender` |
| 연령대 | `age_range` | 가입/갱신 시 참고 (출생연도 보강) |
| 출생 연도 | `birthyear` (+`birthday`) | `member.birth_date` |
| 카카오계정(전화번호) | `phone_number` | `member.phone` |

필수 동의 누락 시 콜백에서 가입을 거절하고 안내 메시지를 반환합니다.
이메일 중복(기존 LOCAL 계정 등)도 자동 병합하지 않고 충돌로 처리합니다.

## Naver developers

네이버 로그인 API는 백엔드에 유지되지만, 현재 로그인/회원가입 화면 버튼은 비노출(주석 처리)입니다.

## Environment

```env
FRONTEND_URL=https://btc-camel.com
OAUTH_PUBLIC_CALLBACK_BASE=https://btc-camel.com/api/shop
KAKAO_ENABLED=true
KAKAO_CLIENT_ID=...
KAKAO_CLIENT_SECRET=...
NAVER_ENABLED=true
NAVER_CLIENT_ID=...
NAVER_CLIENT_SECRET=...
COOKIE_SECURE=true
```

## Security notes

- Never log access tokens / client secrets
- Validate OAuth `state` (server-side, single use, TTL)
- Do not put Kakao/Naver tokens in localStorage or response JSON
- Multi-instance deployments should replace the in-memory state store with Redis (or similar)
