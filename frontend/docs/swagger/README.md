# Swagger API Contract

This folder stores the frontend-facing API contract generated from the deployed Swagger endpoint.

- Source: `https://chamchamcham.jaehyuns.com/v3/api-docs`
- Canonical snapshot: `openapi.json`
- Snapshot hash: `openapi.sha256`
- Human summary: `summary.md`

The Notion-generated `frontend/docs/API 명세서/` folder is archived only. Never use or
regenerate it for frontend API decisions.
Do not hand-edit `openapi.json`; run `python3 scripts/sync_swagger_spec.py --write`.

## Frontend Integration Notes

- Swagger is the frontend API contract source from 2026-07-08 onward.
- Email/password auth endpoints exist in Swagger, but the current app flow is social-login-only; keep
  `/auth/signup`, `/auth/login`, `/auth/email/send-code`, and `/auth/email/verify-code` out of app code until a
  product screen exists.
- Keep `/api/v1/test/ping` and `/api/v1/test/me` out of production app code. They are backend diagnostics.
- Current Swagger has no Voice, FarmingRecord, Report, or Policy endpoints.
- Current Swagger includes member and community controllers beyond the older frontend agent note.
- Current Swagger marks `FarmRequest.dataSource` as required.
- Current Swagger may omit `CommentResponse.replies`; the app treats a missing key as an empty reply list.
