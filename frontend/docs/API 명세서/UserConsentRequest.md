<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# UserConsentRequest

> ⬆ 상위: [API 명세서](README.md)

### Fields
- legalDocumentId: uuid, required.
- agreed: boolean, required.
### Rule
사용자 동의는 user_consents에 저장하며 agreed_at을 기록한다.
