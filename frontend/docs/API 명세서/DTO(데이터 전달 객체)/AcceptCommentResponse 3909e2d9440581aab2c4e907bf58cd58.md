# AcceptCommentResponse

API 분류: API Response
태그: [커뮤니티] 커뮤니티

## Fields

- postId: uuid, required.
- acceptedCommentId: uuid, required.

## Rule

QUESTION 게시글만 답변 채택 가능하며 작성자만 채택할 수 있다. 채택 댓글은 1개만 허용한다.