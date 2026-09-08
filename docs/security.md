# Security notes (initial)

- Passwords: BCrypt only; never log plaintext
- Tokens: HttpOnly + SameSite=Lax; Secure in production; never Web Storage
- Refresh tokens stored as hash only; rotation on refresh
- CSRF protection on cookie-authenticated state-changing requests
- Admin APIs enforce `ADMIN` on server
- Mask PII in logs (phone, address, tokens)
- No stack traces in API responses
- `.env` and secrets in `.gitignore`
