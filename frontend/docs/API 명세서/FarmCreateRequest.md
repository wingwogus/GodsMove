<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# FarmCreateRequest

> ⬆ 상위: [API 명세서](README.md)

### Fields
- name: string, required.
- region: string, required.
- city: string, required.
- street: string, optional.
- farmType: string, optional. 재배/채취 등.
### Rule
온보딩 완료 시 기본 농장 1개가 반드시 생성된다.
