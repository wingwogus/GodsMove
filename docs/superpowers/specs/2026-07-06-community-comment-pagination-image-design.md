# Community Comment Pagination And Image Design

Date: 2026-07-06
Status: Approved

## Purpose

Add cursor pagination to community comment 조회 and allow one optional image on
each comment.

The design keeps the change small:

- Page only root comments.
- Include all replies for the root comments in the current page.
- Allow at most one image per root comment or reply.
- Reuse the existing Cloudinary-backed media upload flow.

## Product Decisions

- Comment list order changes to latest root comments first:
  `createdAt desc, id desc`.
- Pagination applies only to root comments where `parentComment is null`.
- Replies are not independently paginated in this iteration.
- Replies for each returned root comment are included in full.
- Replies are ordered oldest first: `createdAt asc, id asc`.
- Deleted comments stay visible as placeholder comments.
- Deleted comments return `body = "삭제된 댓글입니다."` and `imageUrl = null`.
- Comment images are optional.
- Root comments and replies both support one image.
- Comment images use the existing `media/images` upload API.
- Clients upload with `UploadedMediaUsageType.COMMUNITY_POST` and then pass the
  returned `mediaId` when creating a comment.

## Out Of Scope

- Comment update API.
- Multiple images per comment.
- Separate reply pagination.
- Comment sort options.
- Comment like/reaction sorting.
- QueryDSL migration.
- New media cleanup jobs.
- New Cloudinary folder or media usage enum for comments.
- Moderation/reporting behavior.

## API Contract

### List Comments

```http
GET /api/v1/community/posts/{postId}/comments?cursor=&size=
```

Query parameters:

- `cursor`: optional opaque cursor string.
- `size`: optional page size, default `20`.

Response:

```json
{
  "items": [
    {
      "id": "00000000-0000-0000-0000-000000000101",
      "parentCommentId": null,
      "author": {
        "memberId": "00000000-0000-0000-0000-000000000001",
        "nickname": "황기농부",
        "profileImageUrl": null
      },
      "body": "사진을 보면 배수가 문제 같아요.",
      "imageUrl": "https://example.test/comment.jpg",
      "deleted": false,
      "createdAt": "2026-06-12T09:00:00",
      "replies": []
    }
  ],
  "nextCursor": "opaque-cursor"
}
```

`nextCursor` is `null` when there is no next root-comment page.

The response shape changes from a raw comment array to a cursor page:

```kotlin
data class CommentPageResponse(
    val items: List<CommentResponse>,
    val nextCursor: String?
)
```

### Create Comment

```http
POST /api/v1/community/posts/{postId}/comments
```

Request:

```json
{
  "parentCommentId": null,
  "body": "사진을 보면 배수가 문제 같아요.",
  "mediaId": "00000000-0000-0000-0000-000000000601"
}
```

Rules:

- `mediaId` is optional.
- If `mediaId` is present, it must belong to the requesting member.
- If `mediaId` is present, it must be attachable.
- If `mediaId` is present, it must use `UploadedMediaUsageType.COMMUNITY_POST`.
- A comment can reference at most one media row by construction.

Response remains:

```json
{
  "id": "00000000-0000-0000-0000-000000000501"
}
```

## Cursor Contract

Use the existing common `OpaqueCursorCodec`.

Comment cursor payload:

```kotlin
data class CommunityCommentCursorPayload(
    val createdAt: LocalDateTime,
    val id: UUID
)
```

The cursor is generated from the last visible root comment in the current page.
Replies do not affect cursor generation.

Cursor predicate for latest-first root comments:

```text
createdAt < cursor.createdAt
or createdAt = cursor.createdAt and id < cursor.id
```

Malformed or undecodable cursors return `400 Bad Request` through the existing
invalid cursor error path.

## Domain Model

Add one nullable media reference to `CommunityComment`:

```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "media_id")
val media: UploadedMedia? = null
```

This keeps the schema aligned with the one-image rule. A separate mapping table
is unnecessary unless comments later support multiple images.

Soft-deleting a comment does not delete or detach the media row in this
iteration. The API simply hides it by returning `imageUrl = null` for deleted
comments.

## Application Boundary

`CommunityCommentCommand.Create` gains:

```kotlin
val mediaId: UUID?
```

`CommunityCommentResult.Comment` gains:

```kotlin
val imageUrl: String?
```

`CommunityCommentResult` gains:

```kotlin
data class Page(
    val items: List<Comment>,
    val nextCursor: String?
)
```

`CommunityCommentService.list` becomes:

```kotlin
fun list(postId: UUID, cursor: String?, size: Int): CommunityCommentResult.Page
```

Service responsibilities:

- Decode the optional cursor.
- Fetch `size + 1` root comments.
- Trim to `size`.
- Fetch replies for the returned root comment ids.
- Encode `nextCursor` from the last returned root comment only when an overflow
  row exists.
- Convert deleted comments to placeholder body and hidden image.
- Validate optional `mediaId` during create and mark media attached.

## Repository Boundary

Keep repository changes small. Because comment paging has one fixed sort,
`CommunityCommentQueryRepository` is not required yet.

`CommunityCommentRepository` should provide two query methods:

- Fetch root comments for a post by latest-first cursor.
- Fetch replies for a set of root comment ids ordered oldest first.

The root query should load only root comments. The reply query should load only
comments whose `parentComment.id` is in the current root page.

## Error Handling

- `size <= 0` fails with `INVALID_INPUT`.
- Invalid cursor strings fail with `INVALID_CURSOR`.
- `mediaId` not found, owned by another member, already attached, or wrong
  usage type fails with the existing media/community input error pattern used by
  post image attachment.
- Deleted parent comments and reply-to-reply attempts keep the existing invalid
  reply parent behavior.
- Deleted comments are returned with hidden image instead of exposing stale
  media URLs.

## Testing

Application tests:

- Lists root comments latest first.
- Returns only `size` root comments and a `nextCursor` when an overflow root
  comment exists.
- Uses the request cursor to fetch the next root-comment page.
- Includes all replies for the returned root comments.
- Orders replies oldest first.
- Returns deleted comment placeholder body and `imageUrl = null`.
- Creates a root comment with optional `mediaId`.
- Creates a reply with optional `mediaId`.
- Rejects invalid media ownership/status/usage cases.

API tests:

- `GET /posts/{postId}/comments` returns `{ items, nextCursor }`.
- `GET /posts/{postId}/comments?cursor=...&size=...` maps query parameters to
  the service.
- `POST /posts/{postId}/comments` maps optional `mediaId`.
- Comment response includes `imageUrl`.

Repository tests:

- Root comment query applies latest-first order and cursor predicate.
- Reply batch query returns replies only for the requested root ids and orders
  them oldest first.

## YAGNI Notes

- Do not add a comment sort enum. There is only one comment order.
- Do not add a generic pagination abstraction. Reuse `OpaqueCursorCodec` only.
- Do not add a comment-media mapping table while the product rule is one image.
- Do not add a new upload endpoint. Existing image upload already returns the
  `mediaId` needed by comment creation.
- Do not add `UploadedMediaUsageType.COMMUNITY_COMMENT` yet. Comment images do
  not have a separate lifecycle from other community images in this iteration.
- Do not paginate replies until there is a concrete UX or performance need.
