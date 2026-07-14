<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# PolicyRecommendationResponse

> ⬆ 상위: [API 명세서](README.md)

### Fields
- id: uuid, required.
- userId: uuid, required.
- policyProgram: PolicyProgramResponse, required.
- score: number, required.
- reason: string, required.
- createdAt: datetime, required.
### Rule
추천 기준은 지역, 재배 작물, 영농 경력, 농장 유형이다. 종료된 정책은 추천 대상에서 제외한다.
