# CommunityPostUpdateRequest

API 분류: API Request
태그: [커뮤니티] 커뮤니티

## Fields

- postType: enum, optional. GENERAL, QUESTION.
- title: string, optional.
- body: string, optional.
- farmingRecordId: uuid | null, optional. null이면 공유 해제.

## Rule

게시글 작성자만 수정 가능하다.