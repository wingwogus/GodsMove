# Community Post Media Edit And Member Profile Design

Date: 2026-07-07
Status: Approved

## Purpose

Finish two follow-up surfaces after the community MVP:

- Make post image editing behave like a true final-list sync.
- Add member profile 조회 APIs for the authenticated member and public member
  profile views.

This design keeps the existing module boundary:
`api -> application -> domain`.

## Product Decisions

- Image upload continues to use the existing endpoint:
  `POST /api/v1/media/images`.
- Community post images use `UploadedMediaUsageType.COMMUNITY_POST`.
- `SavePostRequest.mediaIds` is the final ordered image list for create and
  update.
- A post may have at most 5 images.
- Post update can keep existing images, add new `TEMP` images, remove old
  images, and reorder images.
- Same-post existing `ATTACHED` images are valid during update.
- `ATTACHED` images already linked to a different post are rejected.
- Deleted images are rejected.
- Removed existing images become `UploadedMedia.status = DELETED`.
- Existing `community_post_media` rows are deleted and recreated from the final
  `mediaIds` order to keep ordering simple.
- New `TEMP` images in the final list become `ATTACHED`.
- Member profile APIs are added under a new member API boundary, not under auth
  or community.
- API DTOs for member profile live under `api.member.dto`.
- Application profile logic lives in a new service such as
  `application.member.MemberProfileService`.

## Out Of Scope

- Physical Cloudinary deletion.
- Async cleanup of abandoned `TEMP` media.
- Profile update API.
- Public member search.
- Privacy settings.
- Follow/friend relationships.
- QueryDSL migration.
- Global search.
- New authorization annotations such as `@PreAuthorize`.

## Image Upload Flow

The upload flow stays unchanged:

```http
POST /api/v1/media/images
```

Request uses:

```json
{
  "usageType": "COMMUNITY_POST",
  "base64Image": "<base64 image data>",
  "originalFilename": "sprout.jpg",
  "contentType": "image/jpeg"
}
```

Server behavior:

- Uploads the decoded image to Cloudinary.
- Creates `UploadedMedia` with:
  - `owner = authenticated member`
  - `mediaType = IMAGE`
  - `usageType = COMMUNITY_POST`
  - `status = TEMP`
- Returns `mediaId` and `imageUrl`.

## Post Create Image Rules

Create uses the same final-list contract as update, but there are no existing
attachments.

Rules:

- `mediaIds.size > 5` returns `COMMUNITY_TOO_MANY_IMAGES`.
- Every media id must exist.
- Every media row must be owned by the author.
- Every media row must have `usageType = COMMUNITY_POST`.
- Every media row must be `TEMP`.
- Duplicate media ids are invalid and should return `INVALID_INPUT`.
- On success, all provided media rows become `ATTACHED`.
- `community_post_media.display_order` follows the request order starting at
  `0`.

## Post Update Image Sync

Update receives `SavePostRequest.mediaIds` as the final ordered image list.

Algorithm:

1. Validate the author owns the post.
2. Validate image count is at most 5.
3. Reject duplicate ids in `mediaIds`.
4. Load existing `community_post_media` rows for the post.
5. Load every requested `UploadedMedia`.
6. For each requested media id in order:
   - It must exist.
   - It must belong to the updating member.
   - It must have `usageType = COMMUNITY_POST`.
   - If it is already linked to this same post, it may be `ATTACHED`.
   - If it is new to this post, it must be `TEMP`.
   - If it is linked to another post, reject with `MEDIA_NOT_ATTACHABLE`.
   - If it is `DELETED`, reject with `MEDIA_NOT_ATTACHABLE`.
7. Compute removed existing media:
   - Existing attached media not present in the final list.
8. Mark removed existing media as `DELETED`.
9. Mark newly added `TEMP` media as `ATTACHED`.
10. Delete all existing `community_post_media` rows for the post.
11. Recreate `community_post_media` rows from the final list and display order.

Important clarification:

`MEDIA_NOT_ATTACHABLE` does not mean "all ATTACHED images are invalid" during
post update. It means:

- `ATTACHED` and linked to the same post: valid keep/reorder.
- `ATTACHED` and linked to another post: invalid.
- `DELETED`: invalid.
- `TEMP`: valid new image.

## Profile API

### My Profile

```http
GET /api/v1/members/me
```

Authentication: required.

Response includes private profile fields:

```json
{
  "memberId": "00000000-0000-0000-0000-000000000001",
  "email": "hwanggi@cham.test",
  "name": "이황기",
  "phone": "010-1000-0001",
  "birthDate": "1986-03-12",
  "nickname": "황기농부",
  "experienceLevel": 2,
  "managementType": "AGRICULTURAL_INDIVIDUAL",
  "profileImageUrl": "https://example.test/profile.jpg",
  "farms": [
    {
      "farmId": "00000000-0000-0000-0000-000000000201",
      "name": "횡성 황기밭",
      "roadAddress": "강원특별자치도 횡성군 둔내면 샘물로 12",
      "jibunAddress": "강원특별자치도 횡성군 둔내면 현천리 101",
      "displayRegion": "강원특별자치도 횡성군"
    }
  ],
  "crops": [
    {
      "cropId": "00000000-0000-0000-0000-000000000301",
      "cropName": "황기"
    }
  ]
}
```

### Public Profile

```http
GET /api/v1/members/{memberId}/profile
```

Authentication: required for this iteration. The returned content is public
safe, but the endpoint is still inside the authenticated API surface.

Response excludes private fields:

```json
{
  "memberId": "00000000-0000-0000-0000-000000000001",
  "nickname": "황기농부",
  "experienceLevel": 2,
  "managementType": "AGRICULTURAL_INDIVIDUAL",
  "profileImageUrl": "https://example.test/profile.jpg",
  "farms": [
    {
      "farmId": "00000000-0000-0000-0000-000000000201",
      "displayRegion": "강원특별자치도 횡성군"
    }
  ],
  "crops": [
    {
      "cropId": "00000000-0000-0000-0000-000000000301",
      "cropName": "황기"
    }
  ]
}
```

Public profile must not expose:

- email
- phone
- name
- birthDate
- full road address
- full jibun address

## displayRegion Rule

`displayRegion` is derived from a farm address with a two-token rule.

Rule:

- Prefer `roadAddress`.
- If `roadAddress` is blank, use `jibunAddress`.
- Split by one or more whitespace characters.
- Return the first two tokens joined by one ASCII space.
- If only one token exists, return that token.
- If no address token exists, return `null`.

Examples:

- `"강원특별자치도 횡성군 둔내면 샘물로 12"` -> `"강원특별자치도 횡성군"`
- `"서울특별시 강남구 테헤란로 1"` -> `"서울특별시 강남구"`
- `"제주특별자치도"` -> `"제주특별자치도"`
- `null` -> `null`

## Application Boundary

Add `MemberProfileService` under `application.member`.

Dependencies:

- `MemberRepository`
- `FarmRepository`
- `MemberCropRepository`

Responsibilities:

- Load the member or throw `MEMBER_NOT_FOUND`.
- Load farms by owner member id.
- Load member crops by member id.
- Build my-profile result with private fields.
- Build public-profile result without private fields.
- Derive `displayRegion`.
- De-duplicate crops by `crop.id` while preserving repository order.

Do not add a new repository abstraction unless the existing repositories cannot
serve the profile read use cases.

## API Boundary

Add `MemberController` under `api.member.controller`.

Endpoints:

- `GET /api/v1/members/me`
- `GET /api/v1/members/{memberId}/profile`

DTOs live in `api.member.dto`, for example:

- `MemberResponses.MyProfileResponse`
- `MemberResponses.PublicProfileResponse`
- `MemberResponses.FarmProfileResponse`
- `MemberResponses.CropProfileResponse`

The controller parses the authenticated member id the same way existing
community controllers do.

## Error Handling

Reuse existing `ErrorCode` values:

- Missing member: `MEMBER_NOT_FOUND`
- Image owned by another member: `MEDIA_NOT_OWNED`
- Image usage is not `COMMUNITY_POST`: `MEDIA_USAGE_MISMATCH`
- Deleted image or image linked to another post: `MEDIA_NOT_ATTACHABLE`
- More than five post images: `COMMUNITY_TOO_MANY_IMAGES`
- Post update by non-author: `COMMUNITY_FORBIDDEN`
- Duplicate `mediaIds`: `INVALID_INPUT`

## Testing

Minimum application tests:

- Updating a post can keep an existing image attached to the same post.
- Updating a post marks removed existing images as `DELETED`.
- Updating a post marks newly added `TEMP` images as `ATTACHED`.
- Updating a post rejects another member's image.
- Updating a post rejects an image already attached to a different post.
- My profile returns private member fields plus farms and crops.
- Public profile excludes private fields and returns farm region/crops.
- `displayRegion` follows the first-two-token rule.

Minimum API/controller tests:

- `GET /api/v1/members/me` maps authenticated member id and returns my profile.
- `GET /api/v1/members/{memberId}/profile` maps path member id and returns public
  profile.
- Public profile response has no private fields.

## Deployment Notes

No schema change is required for this slice if the community MVP schema already
contains:

- `uploaded_media`
- `community_post_media`
- `member.profile_media_id`
- `farm`
- `member_crop`

The behavior change is application-level. Existing post media rows remain valid.
