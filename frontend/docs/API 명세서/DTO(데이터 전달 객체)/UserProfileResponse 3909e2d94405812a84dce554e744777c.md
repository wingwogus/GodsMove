# UserProfileResponse

API 분류: API Response
태그: [회원] 유저 정보

## Fields

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