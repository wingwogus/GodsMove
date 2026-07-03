# SearchResponse

API 분류: API Response
태그: [검색] 검색

## Fields

- query: string, required.
- items: array, required.
- items[].type: enum, required. FARMING_RECORD, COMMUNITY_POST, POLICY_PROGRAM, LEGAL_DOCUMENT.
- items[].id: uuid, required.
- items[].title: string, required.
- items[].snippet: string, optional.
- items[].matchedFields: string[], optional.
- items[].createdAt: datetime | null, optional.
- page: PageResponse metadata, optional.

## Rule

삭제 데이터, 비공개 데이터, 탈퇴한 사용자의 비공개 정보는 검색 결과에서 제외한다. 기본 정렬은 관련도순, 동률이면 최신순이다.