# Member Profile Edit Design

Date: 2026-07-07
Status: Approved

## Purpose

Add a "my profile edit" API that lets an authenticated member update the same
core information collected during onboarding:

- private member profile fields
- profile image
- farm information
- farm crop selections

This is a follow-up to the member profile read API. The design intentionally
keeps the mobile save flow simple while keeping the application service
internals sectioned enough to split later.

## Product Decisions

- Use one API endpoint for the edit screen:
  `PUT /api/v1/members/me/profile`.
- The API request is a full save for basic profile fields.
- `profileMediaId` is the final profile image value:
  - `null`: remove the current profile image.
  - same as current profile image id: keep it.
  - different id: replace with a new `PROFILE` image.
- `farms` contains only farms to update or create.
- Existing farms omitted from `farms` are kept unchanged.
- Farm deletion is out of scope.
- For each requested farm, `cropIds` is the final crop list for that farm.
- `member_crop` rows for a requested farm are deleted and recreated from that
  farm's `cropIds`.
- Internal application flow should be split by section:
  profile fields, profile image, farms, and farm crops.

## Out Of Scope

- Farm deletion.
- Farm soft delete.
- Section-specific profile edit APIs.
- New authorization annotations such as `@PreAuthorize`.
- New repository abstraction layers.
- Cloudinary physical deletion.
- Profile image history.
- Audit log.
- Public profile edit.
- Email update.
- Password update.
- Social account update.

## API

```http
PUT /api/v1/members/me/profile
```

Authentication: required.

The controller parses the authenticated member id the same way existing
authenticated controllers do.

### Request

```json
{
  "name": "이황기",
  "phone": "010-1000-0001",
  "birthDate": "1986-03-12",
  "nickname": "황기농부",
  "experienceLevel": 2,
  "managementType": "AGRICULTURAL_INDIVIDUAL",
  "profileMediaId": null,
  "farms": [
    {
      "farmId": "00000000-0000-0000-0000-000000000101",
      "name": "횡성 황기밭",
      "roadAddress": "강원특별자치도 횡성군 둔내면 샘물로 12",
      "jibunAddress": "강원특별자치도 횡성군 둔내면 현천리 101",
      "latitude": 37.1,
      "longitude": 128.1,
      "pnu": "4273031021101010000",
      "landCategory": "전",
      "areaSqm": 1234.5,
      "areaIsManualEntry": false,
      "boundaryCoordinates": [
        {
          "latitude": 37.1,
          "longitude": 128.1
        }
      ],
      "dataSource": {
        "address": "KAKAO",
        "coordinate": "KAKAO",
        "parcel": "PUBLIC_DATA",
        "landCharacteristic": "PUBLIC_DATA"
      },
      "cropIds": [
        "00000000-0000-0000-0000-000000000201"
      ]
    },
    {
      "farmId": null,
      "name": "새 약초밭",
      "roadAddress": "강원특별자치도 평창군 진부면 새밭길 1",
      "jibunAddress": null,
      "latitude": 37.2,
      "longitude": 128.2,
      "pnu": null,
      "landCategory": null,
      "areaSqm": null,
      "areaIsManualEntry": false,
      "boundaryCoordinates": [],
      "dataSource": {},
      "cropIds": [
        "00000000-0000-0000-0000-000000000202"
      ]
    }
  ]
}
```

Request rules:

- `name`, `phone`, and `nickname` are required and must not be blank.
- `birthDate` is required.
- `experienceLevel` is required and must be between `0` and `100`.
- `managementType` is required and uses the existing enum.
- `farms` is required and must not be empty.
- `farmId = null` means create a new farm.
- `farmId != null` means update an existing farm owned by the member.
- Duplicate non-null `farmId` values are invalid.
- Each farm's `cropIds` is required and must not be empty.
- Duplicate `cropIds` in a farm are de-duplicated while preserving request
  order.

### Response

Return the updated my-profile response shape from the profile read API:

```json
{
  "memberId": "00000000-0000-0000-0000-000000000001",
  "email": "hwanggi@example.com",
  "name": "이황기",
  "phone": "010-1000-0001",
  "birthDate": "1986-03-12",
  "nickname": "황기농부",
  "experienceLevel": 2,
  "managementType": "AGRICULTURAL_INDIVIDUAL",
  "profileImageUrl": null,
  "farms": [
    {
      "farmId": "00000000-0000-0000-0000-000000000101",
      "name": "횡성 황기밭",
      "roadAddress": "강원특별자치도 횡성군 둔내면 샘물로 12",
      "jibunAddress": "강원특별자치도 횡성군 둔내면 현천리 101",
      "displayRegion": "강원특별자치도 횡성군"
    }
  ],
  "crops": [
    {
      "cropId": "00000000-0000-0000-0000-000000000201",
      "cropName": "황기"
    }
  ]
}
```

The response is wrapped in the existing `ApiResponse`.

## Application Boundary

Add a profile edit command under `application.member`:

- `MemberProfileCommand.UpdateMyProfile`
- `MemberProfileCommand.Farm`
- `MemberProfileCommand.FarmBoundaryCoordinate`
- `MemberProfileCommand.FarmDataSource`

Extend `MemberProfileService` with:

```kotlin
fun updateMyProfile(command: MemberProfileCommand.UpdateMyProfile): MemberProfileResult.MyProfile
```

The service should keep the implementation sectioned internally:

1. Load member.
2. Update basic profile fields.
3. Sync profile image.
4. Upsert requested farms.
5. Sync requested farm crops.
6. Return `getMyProfile(memberId)`.

Keep this feature in `MemberProfileService` for this slice.

## Domain Changes

`Member` already has mutable profile fields and `updateProfileMedia(media)`.

`Farm` currently stores most editable fields as immutable constructor
properties. To update existing farms, add `Farm.updateProfile(...)` and convert
only the fields used by that method from `val` to `var`.

Do not add a farm status column, soft delete flag, or new farm history table for
this feature.

Repository needs should stay narrow:

- `FarmRepository.findByOwnerId(ownerId)`
- `MemberCropRepository.findByMember_Id(memberId)` already exists.
- Add `MemberCropRepository.deleteByMember_IdAndFarm_Id(memberId, farmId)` for
  requested farm crop replacement.

## Data Flow

### Basic Profile Fields

The service updates these fields on the member:

- `name`
- `phone`
- `birthDate`
- `nickname`
- `experienceLevel`
- `managementType`

Email is not modified by this API.

### Profile Image

`profileMediaId` is interpreted as the final profile image.

Rules:

- If `profileMediaId == null`:
  - mark the existing `profileMedia` as `DELETED`, if present.
  - set `member.profileMedia = null`.
- If `profileMediaId` equals the current profile media id:
  - keep the current image unchanged.
- If `profileMediaId` is a different id:
  - load the uploaded media or throw `MEDIA_NOT_FOUND`.
  - require the media owner to be the authenticated member.
  - require `usageType = PROFILE`.
  - require status to be `TEMP`.
  - mark the existing profile media as `DELETED`, if present.
  - mark the new media as `ATTACHED`.
  - assign it to the member.

Physical Cloudinary deletion stays out of scope.

### Farms

For each requested farm:

- If `farmId == null`, create a new farm owned by the member.
- If `farmId != null`, load the farm and verify ownership.
- Update the farm fields with the request values.
- Do not delete existing farms that are omitted from the request.

Farm request validation should mirror onboarding farm validation:

- `name` is required and not blank.
- `roadAddress` is required and not blank.
- `latitude` is required.
- `longitude` is required.
- `areaSqm`, when present, must be greater than zero.
- Boundary coordinates, when present, must contain latitude and longitude.

### Farm Crops

For each requested farm:

1. De-duplicate `cropIds` preserving request order.
2. Load all requested crops.
3. If any crop id does not exist, throw `CROP_NOT_FOUND`.
4. Delete existing `member_crop` rows for this member and farm.
5. Recreate `member_crop` rows for the final crop list.

`member_crop` soft delete is not added.

## Error Handling

Reuse existing `ErrorCode` values:

- Missing member: `MEMBER_NOT_FOUND`
- Invalid request shape, empty `farms`, empty `cropIds`, duplicate farm ids:
  `INVALID_INPUT`
- Missing farm or farm not owned by the member: `RESOURCE_NOT_FOUND`
- Missing crop: `CROP_NOT_FOUND`
- Missing profile media: `MEDIA_NOT_FOUND`
- Media owned by another member: `MEDIA_NOT_OWNED`
- Media usage is not `PROFILE`: `MEDIA_USAGE_MISMATCH`
- Media is not attachable: `MEDIA_NOT_ATTACHABLE`

No new error codes are required for this slice.

## Testing

Minimum application tests:

- Updates basic profile fields.
- Keeps profile image when `profileMediaId` equals the current image id.
- Replaces profile image and marks the previous image `DELETED`.
- Removes profile image when `profileMediaId = null`.
- Rejects another member's profile image.
- Rejects a non-`PROFILE` image.
- Updates an existing farm owned by the member.
- Adds a new farm.
- Does not delete existing farms omitted from the request.
- Replaces requested farm crops with the final `cropIds`.
- Rejects another member's farm id.
- Rejects missing crop ids.

Minimum API/controller tests:

- `PUT /api/v1/members/me/profile` maps authenticated member id to command.
- Request maps basic profile fields, profile image id, farm fields, and crop ids.
- Response returns the updated my-profile DTO.
- Invalid principal returns `UNAUTHORIZED`.

## Deployment Notes

No schema migration is required. Farm field mutability is a Kotlin/JPA domain
change only.

Use a derived Spring Data delete method on `MemberCropRepository`; no custom
query repository is needed for this slice.
