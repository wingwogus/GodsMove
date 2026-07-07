# Community Feature Design

Date: 2026-07-06
Status: Approved

## Purpose

Add the first community backend surface for crop-specific posts, comments,
post likes, post images, profile images, and Cloudinary-backed image upload.
The design follows the existing backend module boundaries:
`api -> application -> domain`.

The MVP keeps the community model small. It does not add reporting,
notifications, admin moderation workflows, QueryDSL, or global search yet.

## Product Decisions

- Community boards shown to a member come from that member's `member_crop`
  crops. `member_crop` does not get a unique `(member_id, crop_id)` constraint
  because the same crop may later be registered on multiple farms.
- `GET /api/v1/community/boards` de-duplicates crops in the response when a
  member has the same crop on multiple farms.
- A community post must belong to exactly one crop.
- Post type is an enum: `GENERAL`, `QUESTION`.
- There is no accepted-answer feature. Remove the old `accepted_answer`
  comment concept from the community model.
- Posts and comments use soft delete with `is_deleted`.
- Deleted posts are hidden from list, search, detail, like toggle, and comment
  creation. Deleted comments remain in the tree and are returned as
  "삭제된 댓글입니다.".
- Comments support only one reply level. A reply can point only to a root
  comment.
- Post likes are post-only. Comment likes are out of scope.
- Likes use a single toggle API, not separate like/unlike endpoints.
- Comment and like counts are computed at read time. No denormalized count
  columns or stats table in the MVP.
- A post may share one farming record, but only when the record belongs to the
  author and has the same crop as the post.
- Post edit can change crop, post type, title, body, farming record, and images.
  The same validation rules as create are re-run.
- Community post images are separate from farming-record media and are limited
  to 5 images per post.
- Profile image is optional and stored through the common uploaded-media model.
- `@PreAuthorize` is not used for this feature. Controllers pass the
  authenticated member id to application services; services enforce ownership,
  state, and business rules.

## Data Model

### uploaded_media

Common upload ledger for Cloudinary-backed images.

- `id uuid PK`
- `owner_member_id uuid FK member.id`
- `media_type enum`: `IMAGE`
- `usage_type enum`: `PROFILE`, `COMMUNITY_POST`
- `file_url varchar(2048)`
- `cloudinary_public_id varchar(255)`
- `status enum`: `TEMP`, `ATTACHED`, `DELETED`
- `created_at datetime`
- `updated_at datetime`

Rules:

- `POST /api/v1/media/images` creates `TEMP` media.
- Attaching media to a profile or post changes it to `ATTACHED`.
- Attached media cannot be reused for another target.
- Deleted or replaced media becomes `DELETED`.
- Only the owner can attach the media.

### member

Add:

- `profile_media_id uuid nullable FK uploaded_media.id`

Do not add `profile_image_url`; API responses derive it from
`uploaded_media.file_url`.

### community_post

- `id uuid PK`
- `author_member_id uuid FK member.id`
- `crop_id uuid FK crop.id`
- `farming_record_id uuid nullable FK farming_record.id`
- `post_type enum`: `GENERAL`, `QUESTION`
- `title varchar`
- `body text`
- `is_deleted boolean default false`
- `created_at datetime`
- `updated_at datetime`

The existing `CommunityPostStatus` draft is replaced by `is_deleted`.

### community_post_media

- `id uuid PK`
- `post_id uuid FK community_post.id`
- `uploaded_media_id uuid FK uploaded_media.id`
- `display_order int`
- `created_at datetime`
- `updated_at datetime`

Rules:

- A post may have 0 to 5 rows.
- `display_order` preserves the client-selected order.
- The referenced media must be owned by the post author, have
  `usage_type = COMMUNITY_POST`, and be attachable.

### community_comment

- `id uuid PK`
- `post_id uuid FK community_post.id`
- `parent_comment_id uuid nullable FK community_comment.id`
- `author_member_id uuid FK member.id`
- `body text`
- `is_deleted boolean default false`
- `created_at datetime`
- `updated_at datetime`

The existing `acceptedAnswer` draft is removed.

### community_post_like

- `id uuid PK`
- `post_id uuid FK community_post.id`
- `member_id uuid FK member.id`
- `created_at datetime`

Constraint:

- `unique(post_id, member_id)`

## Public API

All responses use the existing `ApiResponse` wrapper.

### Upload Image

```http
POST /api/v1/media/images
```

Request:

```json
{
  "usageType": "COMMUNITY_POST",
  "base64Image": "<base64 image data>",
  "originalFilename": "sprout.jpg",
  "contentType": "image/jpeg"
}
```

Response:

```json
{
  "mediaId": "00000000-0000-0000-0000-000000000000",
  "imageUrl": "https://res.cloudinary.com/example/image/upload/...",
  "status": "TEMP"
}
```

Validation:

- Authenticated member required.
- `usageType` must be `PROFILE` or `COMMUNITY_POST`.
- `mediaType` is always `IMAGE` in the MVP.
- The decoded payload must be an image accepted by Cloudinary. The decoded
  size limit is server-configured and defaults to 10 MB.

### Complete Onboarding

Existing endpoint:

```http
POST /api/v1/auth/onboarding/complete
```

Add optional field:

```json
{
  "profileMediaId": "00000000-0000-0000-0000-000000000000"
}
```

Validation:

- If present, the media must belong to the member.
- The media must have `usage_type = PROFILE`.
- The media must be attachable.
- On success, `member.profile_media_id` points to the media and the media
  becomes `ATTACHED`.

### List Community Boards

```http
GET /api/v1/community/boards
```

Returns crop boards visible to the authenticated member, based on
`member_crop`. Duplicate crops are removed from the response.

### Search/List Posts

```http
GET /api/v1/community/posts
```

Query parameters:

- `cropId`
- `postType`
- `keyword`
- `likedOnly`
- `mineOnly`
- `cursorCreatedAt`
- `cursorId`
- `size`

Behavior:

- Default sort is latest first.
- Cursor pagination uses `(created_at, id)`.
- Only `is_deleted = false` posts are returned.
- `keyword` searches title and body.
- `likedOnly=true` returns posts liked by the authenticated member.
- `mineOnly=true` returns posts authored by the authenticated member.

Response items include author, crop, post type, title preview, body preview,
first image URL, comment count, like count, `likedByMe`, and cursor fields.

### Create Post

```http
POST /api/v1/community/posts
```

Request:

```json
{
  "cropId": "00000000-0000-0000-0000-000000000000",
  "postType": "QUESTION",
  "title": "황기 발아율이 너무 낮아요 원인이 뭘까요?",
  "body": "올해 황기를 처음 시작한 초보입니다...",
  "farmingRecordId": "00000000-0000-0000-0000-000000000000",
  "mediaIds": [
    "00000000-0000-0000-0000-000000000000"
  ]
}
```

Validation:

- Authenticated member required.
- `cropId`, `postType`, `title`, and `body` are required.
- `mediaIds` length must be 0 to 5.
- Media must be owned by the member, have `usage_type = COMMUNITY_POST`, and be
  attachable.
- If `farmingRecordId` is present, the record must belong to the member and
  its crop must match `cropId`.

### Get Post Detail

```http
GET /api/v1/community/posts/{postId}
```

Returns author, crop, post type, title, body, images, optional farming-record
summary, comment count, like count, and `likedByMe`.

Deleted posts are not returned.

### Update Post

```http
PATCH /api/v1/community/posts/{postId}
```

The author can change crop, post type, title, body, farming record, and images.
The same ownership, crop, and media rules as create are applied.

Deleted posts cannot be updated.

### Delete Post

```http
DELETE /api/v1/community/posts/{postId}
```

The author can soft-delete the post by setting `is_deleted = true`.

### List Comments

```http
GET /api/v1/community/posts/{postId}/comments
```

Returns root comments with one-level replies. Deleted comments are included but
their response body is "삭제된 댓글입니다.".

### Create Comment

```http
POST /api/v1/community/posts/{postId}/comments
```

Request:

```json
{
  "parentCommentId": "00000000-0000-0000-0000-000000000000",
  "body": "황기 종자는 스크래치 작업이 도움이 됩니다."
}
```

Validation:

- The post must exist and not be deleted.
- If `parentCommentId` is present, it must belong to the same post.
- A parent comment must be a root comment.
- A deleted comment cannot be used as a parent.

### Delete Comment

```http
DELETE /api/v1/community/comments/{commentId}
```

The author can soft-delete the comment by setting `is_deleted = true`.

There is no comment update API in the MVP.

### Toggle Post Like

```http
POST /api/v1/community/posts/{postId}/like-toggle
```

Behavior:

- If the member has not liked the post, create `community_post_like`.
- If the member has already liked the post, delete the like row.
- Deleted posts cannot be liked.

Response:

```json
{
  "liked": true,
  "likeCount": 9
}
```

## Application Structure

Keep the first implementation small:

```text
api.community
api.media

application.community
- CommunityPostService
- CommunityCommentService
- CommunityPostSearchCondition
- CommunityPostResult
- CommunityCommentResult

application.media
- MediaUploadService
- ImageUploader

domain.community
- CommunityPost
- CommunityPostMedia
- CommunityComment
- CommunityPostLike
- CommunityPostType
- CommunityPostRepository
- CommunityPostQueryRepository
- CommunityCommentRepository
- CommunityPostLikeRepository

domain.media
- UploadedMedia
- UploadedMediaType
- UploadedMediaUsageType
- UploadedMediaStatus
- UploadedMediaRepository
```

`ImageUploader` is an application port so tests can avoid real Cloudinary
network calls. A Cloudinary implementation can live in the API module, matching
the existing external-client pattern.

`CommunityPostLikeService`, global `SearchService`, search providers, media
cleanup jobs, and QueryDSL are intentionally not part of the MVP.

## Search And Pagination

Use `CommunityPostSearchCondition` as the application-level query input:

```kotlin
data class CommunityPostSearchCondition(
    val memberId: UUID,
    val cropId: UUID?,
    val postType: CommunityPostType?,
    val keyword: String?,
    val likedOnly: Boolean,
    val mineOnly: Boolean,
    val cursorCreatedAt: LocalDateTime?,
    val cursorId: UUID?,
    val size: Int
)
```

The domain query boundary is `CommunityPostQueryRepository.search(condition)`.
The first implementation can use a small custom JPA query implementation. When
QueryDSL is introduced later, only this query implementation should change.

Global search across community, farming records, policies, and other features
is out of scope for this spec. This spec only avoids API and service shapes
that would block a future global-search layer.

## Error Handling

Expected failures use `BusinessException(ErrorCode.X)` and the existing global
exception handler.

New error cases should cover:

- post not found or deleted
- comment not found
- crop not found
- farming record not found or not owned by member
- farming record crop mismatch
- media not found
- media not owned by member
- media usage mismatch
- media already attached or deleted
- too many post images
- forbidden post/comment mutation
- invalid reply parent
- Cloudinary upload failure

## Testing

Application tests:

- create post with crop, optional farming record, and up to 5 images
- reject farming record owned by another member
- reject farming record with a different crop
- reject more than 5 post images
- update post re-runs the same validations
- soft-delete post hides it from list/detail/like/comment creation
- create root comment and one-level reply
- reject reply-to-reply
- delete comment returns "삭제된 댓글입니다." in list results
- toggle like creates then removes a like row
- `likedOnly` and `mineOnly` search conditions return the expected posts
- attach profile media during onboarding
- reject already-attached media reuse

API tests:

- request validation for post create/update, comment create, and image upload
- cursor-list response shape
- like-toggle response shape
- onboarding request accepts optional `profileMediaId`

Media tests:

- `MediaUploadService` persists `TEMP` media after uploader success
- Cloudinary failures map to a stable business error
- uploader is faked in tests; no real Cloudinary network calls

## Non-Goals

- report API and report tables
- notification creation or delivery
- admin moderation and hidden/blocked statuses
- comment likes
- accepted answers
- comment edit API
- global search API
- QueryDSL dependency
- denormalized comment/like counters
- media TTL cleanup or scheduled Cloudinary deletion
