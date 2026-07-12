# Swagger API Summary

- Source title: `ChamChamCham API`
- Version: `v1`
- SHA-256: `46184ce3a531514753f11f8d995feef908d773eda2a62763ac7a1ba0f0fa81b5`
- Paths: `35`
- Operations: `42`
- Schemas: `99`

## Operations

| Method | Path | operationId |
| --- | --- | --- |
| POST | `/api/v1/admin/policies/sync-jobs` | `createJob` |
| GET | `/api/v1/admin/policies/sync-jobs/{jobId}` | `getJob` |
| POST | `/api/v1/auth/apple/login` | `appleLogin` |
| POST | `/api/v1/auth/email/send-code` | `sendVerificationCode` |
| POST | `/api/v1/auth/email/verify-code` | `verifyEmailCode` |
| POST | `/api/v1/auth/kakao/login` | `kakaoLogin` |
| POST | `/api/v1/auth/login` | `login` |
| POST | `/api/v1/auth/logout` | `logout` |
| POST | `/api/v1/auth/naver/login` | `naverLogin` |
| POST | `/api/v1/auth/onboarding/complete` | `completeOnboarding` |
| POST | `/api/v1/auth/reissue` | `reissue` |
| POST | `/api/v1/auth/signup` | `signUp` |
| GET | `/api/v1/community/boards` | `listBoards` |
| DELETE | `/api/v1/community/comments/{commentId}` | `deleteComment` |
| GET | `/api/v1/community/posts` | `listPosts` |
| POST | `/api/v1/community/posts` | `createPost` |
| DELETE | `/api/v1/community/posts/{postId}` | `deletePost` |
| GET | `/api/v1/community/posts/{postId}` | `getPost` |
| PATCH | `/api/v1/community/posts/{postId}` | `updatePost` |
| GET | `/api/v1/community/posts/{postId}/comments` | `listComments` |
| POST | `/api/v1/community/posts/{postId}/comments` | `createComment` |
| POST | `/api/v1/community/posts/{postId}/like-toggle` | `toggleLike` |
| GET | `/api/v1/crops` | `listCrops` |
| GET | `/api/v1/crops/categories` | `listCategories` |
| GET | `/api/v1/crops/categories/{category}/crops` | `listCropsByCategory` |
| GET | `/api/v1/farming-records` | `listRecords` |
| POST | `/api/v1/farming-records` | `createRecord` |
| DELETE | `/api/v1/farming-records/{recordId}` | `deleteRecord` |
| GET | `/api/v1/farming-records/{recordId}` | `getRecord` |
| PATCH | `/api/v1/farming-records/{recordId}` | `updateRecord` |
| GET | `/api/v1/farms/{farmId}/weather` | `getCurrentWeather` |
| POST | `/api/v1/media/images` | `uploadImage` |
| GET | `/api/v1/members/me` | `getMyProfile` |
| GET | `/api/v1/members/me/farm-crops` | `getMyFarmCrops` |
| PUT | `/api/v1/members/me/profile` | `updateMyProfile` |
| GET | `/api/v1/members/{memberId}/profile` | `getPublicProfile` |
| GET | `/api/v1/policies/recommendations` | `listRecommendations` |
| GET | `/api/v1/policies/{policyProgramId}` | `getProgramDetail` |
| GET | `/api/v1/search` | `search` |
| GET | `/api/v1/test/me` | `getMyInfo` |
| GET | `/api/v1/test/ping` | `ping` |
| GET | `/api/v1/work-types` | `listWorkTypes` |

## Schemas

- `ApiError`
- `ApiResponseCommentIdResponse`
- `ApiResponseCommentPageResponse`
- `ApiResponseCurrentWeatherResponse`
- `ApiResponseLikeToggleResponse`
- `ApiResponseListBoardResponse`
- `ApiResponseListCategoryResponse`
- `ApiResponseListCropResponse`
- `ApiResponseListFarmCropsResponse`
- `ApiResponseListWorkTypeResponse`
- `ApiResponseLoginResponse`
- `ApiResponseMyProfileResponse`
- `ApiResponseObject`
- `ApiResponseOnboardingCompleteResponse`
- `ApiResponsePolicyProgramDetailResponse`
- `ApiResponsePolicyRecommendationPageResponse`
- `ApiResponsePolicySyncJobDetailResponse`
- `ApiResponsePolicySyncJobSummaryResponse`
- `ApiResponsePostDetailResponse`
- `ApiResponsePostIdResponse`
- `ApiResponsePostPageResponse`
- `ApiResponsePublicProfileResponse`
- `ApiResponseRecordDetailResponse`
- `ApiResponseRecordIdResponse`
- `ApiResponseRecordPageResponse`
- `ApiResponseTokenResponse`
- `ApiResponseUnit`
- `ApiResponseUploadedImageResponse`
- `AppleLoginRequest`
- `AuthorResponse`
- `BoardResponse`
- `BoundaryCoordinateRequest`
- `CategoryResponse`
- `CommentIdResponse`
- `CommentPageResponse`
- `CommentResponse`
- `CompleteOnboardingRequest`
- `CreateCommentRequest`
- `CropProfileResponse`
- `CropResponse`
- `CurrentWeatherResponse`
- `DataSourceRequest`
- `EnumOptionResponse`
- `FarmBoundaryCoordinateResponse`
- `FarmCropsResponse`
- `FarmDataSourceResponse`
- `FarmRequest`
- `FarmResponse`
- `FertilizingDetailRequest`
- `FertilizingDetailResponse`
- `FieldResponse`
- `HarvestDetailRequest`
- `HarvestDetailResponse`
- `KakaoLoginRequest`
- `LikeToggleResponse`
- `LoginRequest`
- `LoginResponse`
- `MemberProfileResponse`
- `MyFarmResponse`
- `MyProfileResponse`
- `NaverLoginRequest`
- `OnboardingCompleteResponse`
- `OnboardingResponse`
- `PestControlDetailRequest`
- `PestControlDetailResponse`
- `PlantingDetailRequest`
- `PlantingDetailResponse`
- `PolicyAttachmentResponse`
- `PolicyContactResponse`
- `PolicyProgramDetailResponse`
- `PolicyRecommendationItemResponse`
- `PolicyRecommendationPageResponse`
- `PolicySyncJobDetailResponse`
- `PolicySyncJobSummaryResponse`
- `PostDetailResponse`
- `PostIdResponse`
- `PostPageResponse`
- `PostSummaryResponse`
- `PublicFarmResponse`
- `PublicProfileResponse`
- `RecordDetailResponse`
- `RecordIdResponse`
- `RecordPageResponse`
- `RecordSummaryResponse`
- `ReissueRequest`
- `SavePostRequest`
- `SaveRecordRequest`
- `SendVerificationCodeRequest`
- `SignUpRequest`
- `TokenResponse`
- `UpdateMyProfileRequest`
- `UploadImageRequest`
- `UploadedImageResponse`
- `VerifyEmailCodeRequest`
- `WateringDetailRequest`
- `WateringDetailResponse`
- `WeedingDetailRequest`
- `WeedingDetailResponse`
- `WorkTypeResponse`
