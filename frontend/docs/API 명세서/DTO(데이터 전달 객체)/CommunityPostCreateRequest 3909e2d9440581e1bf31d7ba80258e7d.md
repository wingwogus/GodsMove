# CommunityPostCreateRequest

API 분류: API Request
태그: [커뮤니티] 커뮤니티

## Fields

- postType: enum, required. GENERAL, QUESTION.
- title: string, required.
- body: string, required.
- farmingRecordId: uuid, optional. 영농일지 공유 게시글일 때 사용.

## Rule

영농일지 공유 게시글은 해당 영농일지와 연결될 수 있으며, 작물 정보는 영농일지에서 가져온다.