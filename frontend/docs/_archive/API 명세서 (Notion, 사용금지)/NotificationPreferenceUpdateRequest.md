<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# NotificationPreferenceUpdateRequest

> ⬆ 상위: [API 명세서](README.md)

### Fields
- preferences: array, required.
- preferences[].channel: string, required. PUSH 등.
- preferences[].topic: string, required. POLICY, NOTICE, COMMUNITY, SERVICE 등.
- preferences[].enabled: boolean, required.
### Rule
사용자는 알림 종류별 ON/OFF를 설정할 수 있다.
