# CommunityCommentResponse

API 분류: API Response
태그: [커뮤니티] 커뮤니티

## Fields

- id: uuid, required.
- postId: uuid, required.
- parentCommentId: uuid, optional.
- authorUserId: uuid, required.
- authorNickname: string, required. 탈퇴 회원은 익명 값.
- body: string, required.
- acceptedAnswer: boolean, required.
- isDeleted: boolean, required.
- createdAt: datetime, required.