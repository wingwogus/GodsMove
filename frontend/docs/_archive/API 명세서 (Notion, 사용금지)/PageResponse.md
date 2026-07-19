<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# PageResponse

> ⬆ 상위: [API 명세서](README.md)

### 용도
목록 API의 페이지네이션 공통 래퍼.
### Fields
- items: array, required. 실제 데이터 목록.
- page: number, required. 0부터 시작.
- size: number, required. 요청 page size.
- totalElements: number, required.
- totalPages: number, required.
- hasNext: boolean, required.
