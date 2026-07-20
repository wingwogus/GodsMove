<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# SearchResponse

> ⬆ 상위: [API 명세서](README.md)

### Fields
- query: string, required.
- items: array, required.
- items[].type: enum, required. FARMING_RECORD, COMMUNITY_POST, POLICY_PROGRAM, LEGAL_DOCUMENT.
- items[].id: uuid, required.
- items[].title: string, required.
- items[].snippet: string, optional.
- items[].matchedFields: string[], optional.
- items[].createdAt: datetime | null, optional.
- page: PageResponse metadata, optional.
### Rule
삭제 데이터, 비공개 데이터, 탈퇴한 사용자의 비공개 정보는 검색 결과에서 제외한다. 기본 정렬은 관련도순, 동률이면 최신순이다.
