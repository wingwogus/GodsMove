# _archive — 사용 금지 아카이브

이 폴더는 과거 Notion에서 넘어온 참고용 아카이브다. **현재 개발 결정에 사용하지 않는다.**

## API 명세서 (Notion, 사용금지)

`API 명세서 (Notion, 사용금지)/`는 Notion에서 export된 옛 API 명세다.

- **API 결정에 절대 사용하거나 재생성하지 말 것.**
- 프론트 API 계약의 소스 오브 트루스는 배포된 Swagger 스냅샷 [`../swagger/`](../swagger/)
  (`https://chamchamcham.jaehyuns.com/v3/api-docs`에서 생성).
- 계약을 다시 받으려면 `frontend/`에서 `python3 scripts/sync_swagger_spec.py --write` 실행.
- 근거: `../../AGENTS.md`의 Backend Integration 항목 ("archived Notion artifacts: never use
  or regenerate them for frontend API decisions").

이 폴더를 삭제하지 않고 격리만 하는 이유: 과거 이력 참고용으로만 남겨두되, 실제 소스
(`swagger/`) 바로 옆에 두어 혼동되는 것을 막기 위함.
