# Security notes (initial)

- Passwords: BCrypt only; never log plaintext
- Tokens: HttpOnly + SameSite=Lax; Secure in production; never Web Storage
- Refresh tokens stored as hash only; rotation on refresh
- CSRF protection on cookie-authenticated state-changing requests
- Admin APIs enforce `ADMIN` on server
- Mask PII in logs (phone, address, tokens)
- No stack traces in API responses
- `.env` and secrets in `.gitignore`
- Social login (Kakao/Naver): exchange authorization codes only on the backend; validate OAuth `state`; never expose provider tokens to the browser; do not auto-link LOCAL and social accounts by email
