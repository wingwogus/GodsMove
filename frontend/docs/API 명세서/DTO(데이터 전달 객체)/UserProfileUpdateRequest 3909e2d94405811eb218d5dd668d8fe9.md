# UserProfileUpdateRequest

API 분류: API Request
태그: [회원] 유저 정보

## Fields

- name: string, optional.
- nickname: string, optional.
- region: string, optional.
- experienceLevel: string, optional.
- farmType: string, optional.
- managementType: enum, optional.
- crops: UserCropUpsertRequest[], optional.

## Rule

사용자 정보 수정 후 정책 추천 기준과 Home 추천 정보는 갱신한다. 기존 영농일지는 수정하지 않는다.