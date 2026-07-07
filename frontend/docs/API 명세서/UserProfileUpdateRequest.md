<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# UserProfileUpdateRequest

> ⬆ 상위: [API 명세서](README.md)

### Fields
- name: string, optional.
- nickname: string, optional.
- region: string, optional.
- experienceLevel: string, optional.
- farmType: string, optional.
- managementType: enum, optional.
- crops: UserCropUpsertRequest[], optional.
### Rule
사용자 정보 수정 후 정책 추천 기준과 Home 추천 정보는 갱신한다. 기존 영농일지는 수정하지 않는다.
