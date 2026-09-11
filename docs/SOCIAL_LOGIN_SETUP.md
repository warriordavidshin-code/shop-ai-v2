# Social login setup (Kakao / Naver)

BoutiqueCamel keeps the existing email/password JWT cookie auth and adds Kakao + Naver OAuth
as additional sign-up/login paths. Provider tokens never reach the browser.

## Flow

1. Browser opens `GET /api/shop/auth/{kakao|naver}/login` (Next.js BFF → backend)
2. Backend stores a one-time `state`, redirects to the provider authorize URL
3. Provider redirects to `{OAUTH_PUBLIC_CALLBACK_BASE}/auth/{kakao|naver}/callback`
4. Backend validates `state`, exchanges `code` server-side, finds/creates `member` by
   `(auth_provider, provider_user_id)`, issues the same JWT + refresh cookies, redirects to the shop UI

Use the **frontend BFF URL** as `OAUTH_PUBLIC_CALLBACK_BASE` so `Set-Cookie` attaches to the shop origin:

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

Email collision with an existing LOCAL account does **not** auto-link. The social member is created with `email = null`.

## Kakao developers

1. Create an app at [Kakao Developers](https://developers.kakao.com/)
2. Enable Kakao Login
3. Redirect URI (example local):

```text
http://localhost:3000/api/shop/auth/kakao/callback
```

4. Copy REST API key → `KAKAO_CLIENT_ID`
5. Create client secret → `KAKAO_CLIENT_SECRET`
6. Set `KAKAO_ENABLED=true`

Consent items typically needed: profile nickname/image, account email (optional).

## Naver developers

1. Create an app at [Naver Developers](https://developers.naver.com/)
2. Add Login Open API
3. Callback URL (example local):

```text
http://localhost:3000/api/shop/auth/naver/callback
```

4. Copy Client ID / Secret → `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET`
5. Set `NAVER_ENABLED=true`

## Environment

```env
FRONTEND_URL=http://localhost:3000
OAUTH_PUBLIC_CALLBACK_BASE=http://localhost:3000/api/shop
KAKAO_ENABLED=true
KAKAO_CLIENT_ID=...
KAKAO_CLIENT_SECRET=...
NAVER_ENABLED=true
NAVER_CLIENT_ID=...
NAVER_CLIENT_SECRET=...
```

Production: use HTTPS shop origin for both `FRONTEND_URL` and `OAUTH_PUBLIC_CALLBACK_BASE`,
register the same callback URLs in each developer console, and set `COOKIE_SECURE=true`.

## Security notes

- Never log access tokens / client secrets
- Validate OAuth `state` (server-side, single use, TTL)
- Do not put Kakao/Naver tokens in localStorage or response JSON
- Multi-instance deployments should replace the in-memory state store with Redis (or similar)
