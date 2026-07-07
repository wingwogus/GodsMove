# CommunityPostResponse

API 분류: API Response
태그: [커뮤니티] 커뮤니티

## Fields

- id: uuid, required.
- authorUserId: uuid, required.
- authorNickname: string, required. 탈퇴 회원은 익명 값.
- farmingRecordId: uuid, optional.
- crop: CropResponse | null, optional.
- postType: enum, required. GENERAL, QUESTION.
- title: string, required.
- body: string, required.
- status: enum, required. CREATED, UPDATED, DELETED.
- commentCount: number, required.
- acceptedCommentId: uuid, optional.
- createdAt: datetime, required.