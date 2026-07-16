# Member Withdrawal And Public Community Read Design

Date: 2026-07-16

Status: Approved

Base: `origin/dev` at `d96a4d4d72b9a541e3dfa802b3b572bfa7986322`

## Purpose

Allow unauthenticated clients to read existing community post lists, post
details, and comments, and add authenticated member withdrawal that permanently deletes the
member's owned data, uploaded Cloudinary originals, and active authentication
state.

The design preserves all existing authenticated community behavior. It does
not make community writes, likes, or member-specific board discovery
public.

## Product Decisions

- Public access is limited to the existing post list, post detail, and comment list GET
  endpoints.
- Authenticated list and detail responses retain personalized `likedByMe`
  behavior.
- Guest list and detail responses always return `likedByMe = false`.
- Guest requests may use public filters, including `authorMemberId`, crop,
  post type, keyword, sort, cursor, and size.
- Guest requests with `likedOnly = true` or `mineOnly = true` return `401`
  because those filters require an authenticated subject.
- Member withdrawal is a physical deletion, not a status change or soft
  deletion.
- Withdrawal deletes every row owned by the member, including rows already
  soft-deleted by their own aggregate.
- Shared catalog and reference data is retained.
- Cloudinary originals owned by the member are deleted once after the member
  transaction commits. Provider failures are logged and do not roll back the
  committed account deletion.
- Existing access and refresh tokens cannot be used after withdrawal.

## Non-Goals

- Public community board discovery from a member's registered crops.
- Public create, update, delete, comment, reply, or like operations.
- Anonymous likes or anonymous personalized filters.
- Preserving or anonymizing withdrawn-member posts and comments.
- Soft deletion, withdrawal grace periods, recovery, or account restoration.
- Adding Flyway or another migration framework in this change.
- Adding a durable Cloudinary deletion queue, polling, or scheduled retries.
- Deleting shared crop, pesticide, pest, policy program, legal document, or
  other catalog rows.

## Public API Contract

All responses keep the existing `ApiResponse` wrapper.

### Public post list

```http
GET /api/v1/community/posts
```

No `Authorization` header is required. Existing public query parameters remain
available:

- `authorMemberId`
- `cropId`
- `postType`
- `keyword`
- `sort`
- `cursor`
- `size`

The existing `likedOnly` and `mineOnly` parameters remain available only to an
authenticated request when set to `true`. A guest request that omits them or
sets them to `false` succeeds.

For a guest, every item has:

```json
{
  "likedByMe": false
}
```

For an authenticated member, the current personalized result is unchanged.

### Public post detail

```http
GET /api/v1/community/posts/{postId}
```

No `Authorization` header is required. A guest receives the existing detail
shape with `likedByMe = false`. An authenticated member retains the existing
like lookup.

### Public comment list

```http
GET /api/v1/community/posts/{postId}/comments
```

No `Authorization` header is required. Existing cursor and size parameters and
the current nested reply response shape are unchanged.

### Endpoints that remain protected

- `GET /api/v1/community/boards`
- `POST /api/v1/community/posts`
- `PATCH /api/v1/community/posts/{postId}`
- `DELETE /api/v1/community/posts/{postId}`
- `POST /api/v1/community/posts/{postId}/comments`
- `DELETE /api/v1/community/comments/{commentId}`
- `POST /api/v1/community/posts/{postId}/like-toggle`

Security configuration uses method-specific matchers for the three public GET
routes. It must not permit all methods under `/api/v1/community/posts/**`.

## Withdrawal API Contract

```http
DELETE /api/v1/members/me
Authorization: Bearer <access-token>
```

Success follows the repository's existing controller convention:

```http
HTTP/1.1 200 OK
```

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

The endpoint is authenticated and has no request body. The authenticated
member ID is the only deletion target; clients cannot submit another member's
ID.

After success:

- the member row and all member-owned relational data are gone;
- the stored refresh token is removed;
- an existing access token no longer establishes an authenticated principal;
- Cloudinary original deletion is attempted once after commit.

A token whose member has already been deleted is treated as unauthenticated,
so a repeated withdrawal request returns `401`.

## Community Application Design

`CommunityPostSearchCondition.memberId` and
`CommunityPostQueryRepository.SearchCondition.memberId` become nullable. The
field continues to mean the authenticated viewer, not the optional author
filter. `authorMemberId` remains a separate public filter.

`CommunityController` uses a nullable principal parser only for list and
detail. All protected handlers continue using the required parser.

`CommunityPostService.search` rejects a null viewer when either personalized
filter is enabled:

```text
memberId == null and (likedOnly or mineOnly) -> UNAUTHORIZED
```

The query repository skips the member-like lookup when the viewer is null and
maps every row to `likedByMe = false`. It binds `memberId` only for authenticated
personalized predicates. This avoids null parameter binding and preserves the
current author filter, counts, cursor, and sort semantics.

`CommunityPostService.getDetail` accepts a nullable viewer and only performs
the `existsByPostIdAndMemberId` check when one is present.

## Withdrawal Transaction Design

Keep withdrawal in the existing `MemberProfileService`. The controller remains
responsible only for parsing the authenticated member ID and returning the HTTP
wrapper.

Within one relational database transaction, the service:

1. Locks and loads the member or returns `401` when it no longer exists.
2. Loads all of the member's `UploadedMedia.cloudinaryPublicId` values.
3. Clears `member.profileMedia` and flushes the change to break the
   member-to-media cycle.
4. Physically deletes the member.
5. Publishes the member ID and distinct Cloudinary public IDs in an event that
   is handled only after commit.

The database performs owned-data deletion through `ON DELETE CASCADE` foreign
keys. The service must not duplicate that graph with a long hand-maintained
sequence of repository delete calls.

Refresh-token removal and one Cloudinary delete attempt per public ID run as
post-commit cleanup. Each external failure is isolated and logged without
identifiers. Reissue remains unable to create tokens for a deleted member even
if Redis is temporarily unavailable because the existing reissue flow loads
the member before issuing new tokens.

The JWT authentication filter verifies that the token subject still refers to
an existing member before setting the security context. This makes all old
access tokens unusable immediately after the relational delete. A token for a
missing member is ignored and protected routes return the existing `401`
response.

## Owned Deletion Graph

Direct ownership edges from `member` cover:

- `external_identity.member_id`
- `member_consent.member_id`
- `notification_preference.member_id`
- `farm.owner_member_id`
- `member_crop.member_id`
- `farming_record.member_id`
- `farming_cycle_report.member_id`
- `record_feedback.member_id`
- `report_feedback.member_id`
- `policy_recommendation.member_id`
- `voice_record_session.member_id`
- `community_post.author_member_id`
- `community_comment.author_member_id`
- `community_post_like.member_id`
- `uploaded_media.owner_member_id`

Owned aggregate edges also cascade so direct member deletion cannot be blocked
by deeper children:

- farm -> boundary coordinates and member-crop links;
- farming record -> planting, watering, weeding, fertilizing, pest-control,
  harvest, record media, and record feedback;
- record feedback -> next actions;
- farming-cycle report -> report feedback;
- report feedback -> feedback items;
- voice session -> voice turns;
- community post -> post media, comments, and likes;
- community comment -> replies;
- uploaded media -> post-media and record-media links.

Nullable cross-aggregate references use `ON DELETE SET NULL` so deleting their
target never deletes an otherwise surviving aggregate:

- `member.profile_media_id` when uploaded media is deleted;
- `community_comment.media_id` when uploaded media is deleted;
- `community_post.farming_record_id` when a farming record is deleted;
- `voice_record_session.draft_record_id` when a farming record is deleted;
- `farming_cycle_report.final_harvest_record_id` when a farming record is
  deleted.

The withdrawal service still clears `member.profile_media_id` explicitly
before deleting the member. All remaining owned FKs use either the direct
member cascade above or the explicit aggregate cascade list. The final SQL
must ensure that deleting a member succeeds without deleting shared reference
rows.

Shared references remain `NO ACTION` or equivalent in the ownership direction:

- crop and crop catalog data;
- pesticide and pest catalog data;
- policy programs and policy sync history;
- legal documents;
- any other global lookup or source data.

Hibernate mappings use `@OnDelete(CASCADE)` for local and test schema creation.
Because dev and prod use `ddl-auto: none`, add an idempotent PostgreSQL script
under `backend/docs/db`. The script discovers and replaces the relevant
existing FK constraints rather than assuming Hibernate-generated constraint
names.

## Cloudinary Cleanup

The media storage port deletes by Cloudinary public ID. The adapter calls
provider `destroy` with cache invalidation. Successful deletion and an
already-missing asset are both treated as success.

The after-commit listener attempts each distinct public ID once. A failure is
logged without public IDs, URLs, tokens, provider payloads, or member
identifiers, and processing continues with the remaining IDs. There is no task
table, scheduler, polling, or retry policy in the prototype.

If the relational transaction rolls back, the after-commit listener does not
run, so no Cloudinary original is removed for an account that still exists.

## Error Handling

| Condition | Result |
| --- | --- |
| Guest post list/detail with public filters | `200` |
| Guest `likedOnly=true` or `mineOnly=true` | `UNAUTHORIZED`, `401` |
| Missing or soft-deleted post | existing `COMMUNITY_POST_NOT_FOUND` contract |
| Protected community operation without authentication | existing `401` |
| Withdrawal without a valid existing member principal | existing `401` |
| Relational deletion failure | transaction rolls back; no Cloudinary deletion begins |
| Cloudinary failure after commit | account remains deleted; failure is logged without retry |
| Cloudinary reports asset already missing | cleanup succeeds |

The withdrawal response reports successful account deletion once the
relational transaction is committed. It does not expose Cloudinary identifiers
or provider cleanup status.

## Test Strategy

Follow red-green-refactor for each behavior.

Community API and security tests:

- list, detail, and comments return `200` without authentication;
- guest list maps a null viewer and guest detail maps a null viewer;
- guest responses contain `likedByMe = false`;
- guest personalized filters return `401`;
- authenticated personalized behavior remains unchanged;
- create, update, delete, comment writes, likes, and board discovery remain `401`
  without authentication;
- the public matcher permits only the nested comment GET route and not comment
  writes or other non-GET methods.

Community application and repository tests:

- guest search skips viewer-like lookup and returns false personalization;
- guest detail skips the like existence query;
- guest personalized filters fail before repository access;
- authenticated `likedOnly`, `mineOnly`, and `likedByMe` behavior is preserved;
- author filtering, count, sort, and cursor behavior remains unchanged with a
  nullable viewer.

Withdrawal application tests:

- member media public IDs are captured before the member delete;
- the profile-media reference is cleared;
- member delete and post-commit event publication occur;
- missing members fail before media lookup;
- a transaction failure does not dispatch external deletion;
- refresh-token cleanup is requested after commit;
- each distinct Cloudinary public ID is attempted once after commit;
- one external cleanup failure does not block remaining cleanup.

Persistence integration tests:

- deleting a representative member removes every direct member-owned table;
- nested community, farming, report, coaching, voice, and media rows are also
  removed;
- soft-deleted owned rows are physically removed;
- shared catalog/reference rows remain;
- the generated local/test schema honors the same delete behavior expected by
  the PostgreSQL migration.

Authentication tests:

- a valid token for an existing member authenticates normally;
- the same cryptographically valid token for a deleted member does not create
  a security context;
- the deleted member's refresh token cannot reissue credentials.

Verification runs focused tests first, then `./gradlew check` from `backend`.
The PostgreSQL migration relationship list is reviewed against every JPA
association that reaches a member-owned aggregate.

## Risks And Mitigations

- **Large ownership graph:** DB constraints, a persistence integration test,
  and an explicit migration relationship list reduce silent omissions.
- **Manual production migration:** the script is idempotent and constraint-name
  independent, but it must be applied before deploying application code that
  calls member deletion.
- **External provider is not transactional:** the prototype performs one
  best-effort deletion after commit. A transient provider failure can leave an
  orphaned Cloudinary original and requires manual cleanup.
- **JWTs are stateless:** member existence is checked before authentication so
  a deleted subject cannot keep using a signed access token.
- **Extra authentication lookup:** the existence check adds one lightweight
  repository lookup per valid access-token request. Correct revocation is
  preferred over retaining a seven-day member-ID blacklist after hard delete.
- **Public matcher expansion:** method-specific and path-specific security tests
  prevent accidentally opening comment writes or unrelated nested endpoints.

## Deployment Order

1. Apply the PostgreSQL FK schema script to dev/prod.
2. Deploy application code.
3. Verify anonymous post list, detail, and comment requests.
4. Verify an isolated test-member withdrawal, owned-row deletion, refresh-token
   invalidation, and one-shot Cloudinary cleanup.

Deploying the application before the FK migration is unsupported because
member deletion may fail on existing `NO ACTION` constraints.
