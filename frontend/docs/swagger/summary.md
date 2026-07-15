# Swagger API Summary

- Source title: `ChamChamCham API`
- Version: `v1`
- SHA-256: `5715356eacc78c14154bc91c497d7bb1a3a790958e39f0acb0d5da75f36fab81`
- Paths: `48`
- Operations: `57`
- Schemas: `134`

## Operations

| Method | Path | operationId |
| --- | --- | --- |
| POST | `/api/v1/admin/pesticide-sync` | `sync` |
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
| GET | `/api/v1/farms` | `list` |
| POST | `/api/v1/farms` | `create` |
| GET | `/api/v1/farms/weather` | `getCurrentWeatherForDefaultFarm` |
| GET | `/api/v1/farms/weather/daily` | `getDailyWeatherForDefaultFarm` |
| DELETE | `/api/v1/farms/{farmId}` | `delete` |
| PUT | `/api/v1/farms/{farmId}` | `replace` |
| GET | `/api/v1/farms/{farmId}/weather` | `getCurrentWeather` |
| GET | `/api/v1/farms/{farmId}/weather/daily` | `getDailyWeather` |
| POST | `/api/v1/media/images` | `uploadImage` |
| GET | `/api/v1/members/me` | `getMyProfile` |
| GET | `/api/v1/members/me/farm-crops` | `getMyFarmCrops` |
| PUT | `/api/v1/members/me/profile` | `updateMyProfile` |
| GET | `/api/v1/members/{memberId}/profile` | `getPublicProfile` |
| GET | `/api/v1/pesticides` | `searchPesticides` |
| GET | `/api/v1/pesticides/{pesticideId}/pests` | `listPests` |
| GET | `/api/v1/policies/recommendations` | `listRecommendations` |
| GET | `/api/v1/policies/{policyProgramId}` | `getProgramDetail` |
| GET | `/api/v1/search` | `search` |
| GET | `/api/v1/search/suggestions` | `suggestions` |
| GET | `/api/v1/test/me` | `getMyInfo` |
| GET | `/api/v1/test/ping` | `ping` |
| POST | `/api/v1/voice-sessions` | `createSession` |
| POST | `/api/v1/voice-sessions/{sessionId}/cancel` | `cancel` |
| POST | `/api/v1/voice-sessions/{sessionId}/confirm` | `confirm` |
| PATCH | `/api/v1/voice-sessions/{sessionId}/turns` | `submitTurns` |
| GET | `/api/v1/work-types` | `listWorkTypes` |

## Schemas

- `ApiError`
- `ApiResponseCancelledResponse`
- `ApiResponseCommentIdResponse`
- `ApiResponseCommentPageResponse`
- `ApiResponseConfirmedResponse`
- `ApiResponseCreatedResponse`
- `ApiResponseCurrentWeatherResponse`
- `ApiResponseDailyWeatherResponse`
- `ApiResponseFarmResponse`
- `ApiResponseLikeToggleResponse`
- `ApiResponseListBoardResponse`
- `ApiResponseListCategoryResponse`
- `ApiResponseListCropResponse`
- `ApiResponseListFarmCropsResponse`
- `ApiResponseListFarmResponse`
- `ApiResponseListPestSummaryResponse`
- `ApiResponseListWorkTypeResponse`
- `ApiResponseLoginResponse`
- `ApiResponseMyProfileResponse`
- `ApiResponseObject`
- `ApiResponseOnboardingCompleteResponse`
- `ApiResponsePesticidePageResponse`
- `ApiResponsePesticideSyncResult`
- `ApiResponsePolicyProgramDetailResponse`
- `ApiResponsePolicyRecommendationPageResponse`
- `ApiResponsePolicySyncJobDetailResponse`
- `ApiResponsePolicySyncJobSummaryResponse`
- `ApiResponsePostDetailResponse`
- `ApiResponsePostIdResponse`
- `ApiResponsePostPageResponse`
- `ApiResponseProcessedResponse`
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
- `BoundaryCoordinateResponse`
- `CancelledResponse`
- `CandidateRequest`
- `CandidateResponse`
- `CategoryResponse`
- `CommentIdResponse`
- `CommentPageResponse`
- `CommentResponse`
- `CompleteOnboardingRequest`
- `ConfirmedResponse`
- `CreateCommentRequest`
- `CreatedResponse`
- `CropOptionResponse`
- `CropProfileResponse`
- `CropResponse`
- `CurrentWeatherResponse`
- `DailyWeatherResponse`
- `DataSourceRequest`
- `DataSourceResponse`
- `EnumOptionResponse`
- `FarmCropsResponse`
- `FarmDraftRequest`
- `FarmOptionResponse`
- `FarmRequest`
- `FarmResponse`
- `FertilizingDetail`
- `FertilizingDetailRequest`
- `FertilizingDetailResponse`
- `FieldResponse`
- `ForecastDayResponse`
- `HarvestDetail`
- `HarvestDetailRequest`
- `HarvestDetailResponse`
- `KakaoLoginRequest`
- `LikeToggleResponse`
- `LoginRequest`
- `LoginResponse`
- `MediaResponse`
- `MemberProfileResponse`
- `MyFarmResponse`
- `MyProfileResponse`
- `NaverLoginRequest`
- `OnboardingCompleteResponse`
- `OnboardingResponse`
- `PestControlDetail`
- `PestControlDetailRequest`
- `PestControlDetailResponse`
- `PestSummaryResponse`
- `PesticidePageResponse`
- `PesticideSummaryResponse`
- `PesticideSyncResult`
- `PlantingDetail`
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
- `ProcessedResponse`
- `PublicFarmResponse`
- `PublicProfileResponse`
- `RecordDetailResponse`
- `RecordIdResponse`
- `RecordPageResponse`
- `RecordSummaryResponse`
- `ReissueRequest`
- `SaveFarmRequest`
- `SavePostRequest`
- `SaveRecordRequest`
- `SendVerificationCodeRequest`
- `SignUpRequest`
- `SubmitTurnsRequest`
- `TokenResponse`
- `TurnRequest`
- `UpdateMyProfileRequest`
- `UploadImageRequest`
- `UploadedImageResponse`
- `VerifyEmailCodeRequest`
- `WateringDetail`
- `WateringDetailRequest`
- `WateringDetailResponse`
- `WeedingDetail`
- `WeedingDetailRequest`
- `WeedingDetailResponse`
- `WorkTypeResponse`
