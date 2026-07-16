# Swagger API Summary

- Source title: `ChamChamCham API`
- Version: `v1`
- SHA-256: `5e35272c0615b2f30c0ff8a9f7aaa641d8a3b7ce6d44b89515341ed555f49d2e`
- Paths: `57`
- Operations: `66`
- Schemas: `175`

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
| POST | `/api/v1/coaching/rag/query` | `query` |
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
| GET | `/api/v1/farming-records/{recordId}/feedback` | `getStatus_1` |
| POST | `/api/v1/farming-records/{recordId}/feedback/regenerate` | `regenerate_1` |
| GET | `/api/v1/farming-reports` | `listCompleted` |
| GET | `/api/v1/farming-reports/work-items` | `list_1` |
| GET | `/api/v1/farming-reports/{reportId}` | `getDetail` |
| GET | `/api/v1/farming-reports/{reportId}/feedback` | `getStatus` |
| POST | `/api/v1/farming-reports/{reportId}/feedback/{workType}/regenerate` | `regenerate` |
| GET | `/api/v1/farming-reports/{reportId}/work-types/{workType}` | `getDetail_1` |
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

- `AmountByUnitResponse`
- `ApiError`
- `ApiResponseCancelledResponse`
- `ApiResponseCommentIdResponse`
- `ApiResponseCommentPageResponse`
- `ApiResponseConfirmedResponse`
- `ApiResponseCreatedResponse`
- `ApiResponseCurrentWeatherResponse`
- `ApiResponseDailyWeatherResponse`
- `ApiResponseDetailResponse`
- `ApiResponseFarmResponse`
- `ApiResponseLikeToggleResponse`
- `ApiResponseListBoardResponse`
- `ApiResponseListCategoryResponse`
- `ApiResponseListCropResponse`
- `ApiResponseListFarmCropsResponse`
- `ApiResponseListFarmResponse`
- `ApiResponseListPestSummaryResponse`
- `ApiResponseListResponse`
- `ApiResponseListWorkTypeResponse`
- `ApiResponseLoginResponse`
- `ApiResponseMyProfileResponse`
- `ApiResponseObject`
- `ApiResponseOnboardingCompleteResponse`
- `ApiResponsePageResponse`
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
- `ApiResponseQueryResponse`
- `ApiResponseRecordDetailResponse`
- `ApiResponseRecordIdResponse`
- `ApiResponseRecordPageResponse`
- `ApiResponseStatusResponse`
- `ApiResponseTokenResponse`
- `ApiResponseUnit`
- `ApiResponseUploadedImageResponse`
- `AppleLoginRequest`
- `AuditResponse`
- `AuthorResponse`
- `BoardResponse`
- `BoundaryCoordinateRequest`
- `BoundaryCoordinateResponse`
- `CancelledResponse`
- `CandidateRequest`
- `CandidateResponse`
- `CategoryAmountByUnitResponse`
- `CategoryMethodStatisticsResponse`
- `CategoryResponse`
- `CitationResponse`
- `CommentIdResponse`
- `CommentPageResponse`
- `CommentResponse`
- `CommonStatisticsResponse`
- `CompleteOnboardingRequest`
- `ConfirmedResponse`
- `CountDistributionResponse`
- `CoverageResponse`
- `CreateCommentRequest`
- `CreatedResponse`
- `CropOptionResponse`
- `CropProfileResponse`
- `CropResponse`
- `CurrentWeatherResponse`
- `CycleStatisticsResponse`
- `DailyWeatherResponse`
- `DataSourceRequest`
- `DataSourceResponse`
- `DetailResponse`
- `EnumOptionResponse`
- `FarmCropsResponse`
- `FarmDraftRequest`
- `FarmOptionResponse`
- `FarmRequest`
- `FarmResponse`
- `FeedbackResponse`
- `FertilizingDetail`
- `FertilizingDetailRequest`
- `FertilizingDetailResponse`
- `FertilizingStatisticsResponse`
- `FieldResponse`
- `ForecastDayResponse`
- `GrowthPeriodRangeResponse`
- `HarvestDetail`
- `HarvestDetailRequest`
- `HarvestDetailResponse`
- `HarvestPartStatisticsResponse`
- `HarvestStatisticsResponse`
- `ItemResponse`
- `KakaoLoginRequest`
- `LikeToggleResponse`
- `ListResponse`
- `LoginRequest`
- `LoginResponse`
- `MaterialCategoryStatisticsResponse`
- `MediaResponse`
- `MemberProfileResponse`
- `MetadataResponse`
- `ModelResponse`
- `MyFarmResponse`
- `MyProfileResponse`
- `NaverLoginRequest`
- `NextActionResponse`
- `ObservationResponse`
- `OnboardingCompleteResponse`
- `OnboardingResponse`
- `PageResponse`
- `PestControlDetail`
- `PestControlDetailRequest`
- `PestControlDetailResponse`
- `PestControlStatisticsResponse`
- `PestSummaryResponse`
- `PesticidePageResponse`
- `PesticideSummaryResponse`
- `PesticideSyncResult`
- `PlantingDetail`
- `PlantingDetailRequest`
- `PlantingDetailResponse`
- `PlantingStatisticsResponse`
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
- `PropagationStatisticsResponse`
- `PublicFarmResponse`
- `PublicProfileResponse`
- `QueryRequest`
- `QueryResponse`
- `RecommendationResponse`
- `RecordDetailResponse`
- `RecordIdResponse`
- `RecordPageResponse`
- `RecordQualityResponse`
- `RecordSummaryResponse`
- `ReissueRequest`
- `SaveFarmRequest`
- `SavePostRequest`
- `SaveRecordRequest`
- `SendVerificationCodeRequest`
- `SignUpRequest`
- `SnapshotResponse`
- `StatusResponse`
- `StructuredResultResponse`
- `SubmitTurnsRequest`
- `TargetCountResponse`
- `TokenResponse`
- `TurnRequest`
- `UpdateMyProfileRequest`
- `UploadImageRequest`
- `UploadedImageResponse`
- `VerifyEmailCodeRequest`
- `WateringDetail`
- `WateringDetailRequest`
- `WateringDetailResponse`
- `WateringStatisticsResponse`
- `WeedingDetail`
- `WeedingDetailRequest`
- `WeedingDetailResponse`
- `WeedingStatisticsResponse`
- `WorkTypeResponse`
