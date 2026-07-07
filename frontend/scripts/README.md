# scripts

## sync_api_spec.py — Notion API 명세서 → 로컬 마크다운 동기화

팀 API 명세서가 Notion([공개 사이트](https://wingwogus.notion.site/API-d5b9e2d944058337a774015bc187a6aa))에서 계속 업데이트되는데, 이 페이지는 **다른 워크스페이스의 공개 사이트**라 공식 Notion API/MCP 커넥터로는 DB 행(핵심 DTO 문서)을 가져올 수 없다. 그래서 이 스크립트는 Notion 비공식 web API(`loadPageChunk` / `queryCollection`)로 페이지·하위페이지·DB 행을 전부 크롤링해 `docs/API 명세서/`에 마크다운으로 저장한다. 인증/토큰 불필요(공개 페이지 한정), 의존성 없음(Python 3 표준 라이브러리만).

### 사용

```bash
# frontend/ 에서 실행
python3 scripts/sync_api_spec.py --out "docs/API 명세서"
```

- 최상위 페이지 → `README.md`(엔드포인트 표 + DTO 표 + 링크)
- 엔드포인트/DTO/하위페이지 → 각각 개별 `.md`, 상호 링크(`[LoginResponse](LoginResponse.md)`)로 연결
- 실행 시 `--out` 폴더의 기존 `.md`를 지우고 새로 생성한다(스테일 파일 정리).
- 생성 파일은 자동 생성물이므로 **직접 수정하지 말고** 스크립트를 재실행한다.

### 옵션

- `--out DIR` : 출력 폴더 (기본 `out`). 리포에는 `"docs/API 명세서"` 사용.
- `--cache DIR` : HTTP 응답을 디스크에 캐시(개발용). 반복 실행 시 재요청 없이 빨라지고 레이트리밋(429)을 피한다. **최신 내용으로 동기화할 땐 붙이지 말 것**(캐시가 오래된 응답을 재사용). 캐시 폴더는 리포 밖(예: `/tmp`)에 둔다.

### 주의

- Notion 비공식 API는 레이트리밋이 있어 요청 간 1.2초 간격 + 429 백오프를 둔다. 전체 동기화에 대략 2~3분 걸린다.
- 페이지 구조(collection/toggle 등)가 크게 바뀌면 렌더링이 일부 누락될 수 있다 — 결과 diff를 확인하고 필요 시 스크립트를 보완한다.
- 페이지 ID/스페이스 ID는 스크립트 상단 상수(`ROOT_PAGE_ID`, `SPACE_ID`)에 하드코딩돼 있다. Notion 원본이 이전되면 갱신한다.
