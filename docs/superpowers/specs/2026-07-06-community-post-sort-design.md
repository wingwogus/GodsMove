# Community Post Sort Design

Date: 2026-07-06
Status: Approved

## Purpose

Add explicit sorting to the community post list before the community PR is
merged into `dev`.

The sort contract must support latest, like-count, comment-count, and popularity
ordering while keeping the public paging API simple. The design keeps the
existing backend boundaries:
`api -> application -> domain`.

## Product Decisions

- Community post sorting is controlled by `CommunityPostSort`.
- Supported values are `LATEST`, `LIKE`, `COMMENT`, and `POPULAR`.
- `LATEST` remains the default when the client omits `sort`.
- `POPULAR` is a simple engagement score: `likeCount + commentCount`.
- No time-decay, weighted score, ranking table, or denormalized count column is
  added in this iteration.
- Cursor state is not persisted. It is encoded into an opaque cursor string and
  decoded by the server on the next request.
- The client treats the cursor as an opaque value and does not calculate or
  inspect score values.
- Cursor encoding and decoding use a small common codec so future infinite
  scroll APIs can reuse the transport format without inheriting community sort
  rules.

## API Contract

### List Posts

```http
GET /api/v1/community/posts
```

Query parameters:

- `cropId`
- `postType`
- `keyword`
- `likedOnly`
- `mineOnly`
- `sort`: `LATEST`, `LIKE`, `COMMENT`, `POPULAR`; default `LATEST`
- `cursor`: opaque cursor string
- `size`

Response:

```json
{
  "items": [],
  "nextCursor": null
}
```

`nextCursor` is `null` when the response has no next cursor candidate. The
existing separate cursor fields are replaced by the single opaque `cursor`
contract for this endpoint before the community feature is merged.

## Sort Semantics

All sorts use deterministic tie-breakers so cursor pagination can continue
without duplicate rows caused by equal scores.

```text
LATEST  = createdAt desc, id desc
LIKE    = likeCount desc, createdAt desc, id desc
COMMENT = commentCount desc, createdAt desc, id desc
POPULAR = (likeCount + commentCount) desc, createdAt desc, id desc
```

For `LATEST`, the effective cursor score is not needed. For count-based sorts,
the effective score is:

```text
LIKE    -> likeCount
COMMENT -> commentCount
POPULAR -> likeCount + commentCount
```

## Cursor Contract

The cursor is a Base64 URL-safe encoded JSON payload. It is not stored in a
database table or server-side cache.

Internal payload:

```json
{
  "sort": "LIKE",
  "score": 8,
  "createdAt": "2026-06-12T09:00:00",
  "id": "00000000-0000-0000-0000-000000000101"
}
```

Rules:

- `sort` inside the cursor must match the request `sort`.
- `score` is present for `LIKE`, `COMMENT`, and `POPULAR`.
- `score` is omitted or `null` for `LATEST`.
- Malformed, undecodable, or mismatched cursors fail with `400 Bad Request`.
- Cursor manipulation is not treated as a security boundary in this iteration.
  A manipulated valid cursor can only change the list position.

### Common Cursor Codec

Cursor serialization belongs in a small common application component:

```kotlin
class OpaqueCursorCodec {
    fun encode(payload: Any): String
    fun <T : Any> decode(cursor: String, payloadType: Class<T>): T
}
```

The codec is responsible only for:

- JSON serialization and deserialization.
- Base64 URL-safe encoding and decoding.
- Converting malformed cursor input into a consistent application error.

Community keeps its own payload and validation:

```kotlin
data class CommunityPostCursorPayload(
    val sort: CommunityPostSort,
    val score: Long?,
    val createdAt: LocalDateTime,
    val id: UUID
)
```

This keeps the reusable part small. Other infinite scroll APIs can define their
own payload classes and reuse `OpaqueCursorCodec`, but they do not share
community-specific sort names, score rules, or query predicates.

Cursor predicates:

```text
LATEST:
  createdAt < cursor.createdAt
  or createdAt = cursor.createdAt and id < cursor.id

LIKE / COMMENT / POPULAR:
  score < cursor.score
  or score = cursor.score and createdAt < cursor.createdAt
  or score = cursor.score and createdAt = cursor.createdAt and id < cursor.id
```

## Application Boundary

`CommunityPostSearchCondition` gains:

```kotlin
val sort: CommunityPostSort
val cursor: String?
```

`CommunityPostResult.Page` exposes:

```kotlin
val items: List<PostSummary>
val nextCursor: String?
```

The application service decodes the request cursor, validates that it matches
the requested sort, passes structured cursor values into the domain query
repository, and encodes the next cursor from the last returned row.

The application service uses the common `OpaqueCursorCodec` for the raw string
conversion, then applies community-specific validation to the decoded
`CommunityPostCursorPayload`.

## Domain Query Boundary

`CommunityPostQueryRepository.SearchCondition` gains:

```kotlin
val sort: CommunityPostSort
val cursor: CommunityPostCursor?
```

`CommunityPostQueryRepository.Row` must include the row's effective sort score:

```kotlin
val score: Long?
```

The implementation computes comment and like counts in the main post selection
query for sort and cursor predicates. Thumbnail lookup and `likedByMe` lookup
remain separate batch queries.

The query implementation can continue using JPQL for this iteration. QueryDSL
is still deferred until search and sorting rules become broad enough to justify
replacing the repository implementation.

## Error Handling

- Missing `sort` defaults to `LATEST`.
- Unknown `sort` values fail through normal enum request binding.
- A cursor whose embedded sort does not match request `sort` fails with
  `400 Bad Request`.
- A cursor with invalid JSON, invalid UUID, invalid date, or missing required
  fields fails with `400 Bad Request`.
- If a count-based sort receives a cursor without score, the cursor is invalid.

## Testing

Repository tests:

- `LATEST` returns active posts by latest first and supports cursor pagination.
- `LIKE` returns posts by like count descending, then latest first.
- `COMMENT` returns posts by non-deleted comment count descending, then latest
  first.
- `POPULAR` returns posts by `likeCount + commentCount` descending, then latest
  first.
- Count-based cursor pagination returns the next page without duplicating the
  last row from the previous page.

Application tests:

- Search decodes the incoming cursor, passes structured cursor values to the
  query repository, and returns an encoded `nextCursor`.
- Cursor sort mismatch is rejected.
- Malformed cursor is rejected.

Controller tests:

- `sort` and `cursor` query parameters map into `CommunityPostSearchCondition`.
- Omitting `sort` maps to `LATEST`.
- Response contains `nextCursor`.

## Out of Scope

- QueryDSL migration.
- Weighted popularity.
- Time-decayed ranking.
- Persisted ranking snapshots.
- Signed cursor payloads.
- Generic pagination framework or shared query predicate builder.
- Admin-only moderation sort modes.
