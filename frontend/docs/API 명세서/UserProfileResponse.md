<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# UserProfileResponse

> ⬆ 상위: [API 명세서](README.md)

### Fields
- id: uuid, required.
- email: string, optional.
- phone: string, optional.
- status: enum, required. ACTIVE, WITHDRAWN.
- name: string, required.
- nickname: string, required.
- region: string, required.
- experienceLevel: string, optional.
- farmType: string, optional.
- managementType: enum, required.
- createdAt: datetime, required.
- updatedAt: datetime, optional.
- withdrawnAt: datetime, optional.
- farms: FarmResponse[], optional.
- crops: UserCropResponse[], optional.
