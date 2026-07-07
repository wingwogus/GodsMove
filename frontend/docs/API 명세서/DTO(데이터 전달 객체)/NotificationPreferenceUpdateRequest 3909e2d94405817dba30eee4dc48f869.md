# NotificationPreferenceUpdateRequest

API 분류: API Request
태그: [알림] 알림

## Fields

- preferences: array, required.
- preferences[].channel: string, required. PUSH 등.
- preferences[].topic: string, required. POLICY, NOTICE, COMMUNITY, SERVICE 등.
- preferences[].enabled: boolean, required.

## Rule

사용자는 알림 종류별 ON/OFF를 설정할 수 있다.