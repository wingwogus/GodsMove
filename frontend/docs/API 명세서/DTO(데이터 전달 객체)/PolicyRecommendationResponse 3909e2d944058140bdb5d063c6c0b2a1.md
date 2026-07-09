# PolicyRecommendationResponse

API 분류: API Response
태그: [정책] 정책

## Fields

- id: uuid, required.
- userId: uuid, required.
- policyProgram: PolicyProgramResponse, required.
- score: number, required.
- reason: string, required.
- createdAt: datetime, required.

## Rule

추천 기준은 지역, 재배 작물, 영농 경력, 농장 유형이다. 종료된 정책은 추천 대상에서 제외한다.