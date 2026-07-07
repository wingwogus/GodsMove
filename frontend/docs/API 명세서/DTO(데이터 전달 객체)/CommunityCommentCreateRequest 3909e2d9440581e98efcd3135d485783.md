# CommunityCommentCreateRequest

API 분류: API Request
태그: [커뮤니티] 커뮤니티

## Fields

- parentCommentId: uuid, optional. null이면 최상위 댓글.
- body: string, required.

## Rule

답글은 기존 댓글에만 작성 가능하다. DB 기준 답글의 답글도 허용한다.