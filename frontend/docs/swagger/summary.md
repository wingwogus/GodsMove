# Swagger API Summary

- Source title: `ChamChamCham API`
- Version: `v1`
- SHA-256: `332e4cd7e249f9682d57928b1631cef2a2918727353c5299f0c142928f9feac8`
- Paths: `59`
- Operations: `69`
- Schemas: `172`

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
| DELETE | `/api/v1/farms/{farmId}` | `delete` |
| PUT | `/api/v1/farms/{farmId}` | `replace` |
| POST | `/api/v1/media/images` | `uploadImage` |
| DELETE | `/api/v1/members/me` | `withdraw` |
| GET | `/api/v1/members/me` | `getMyProfile` |
| GET | `/api/v1/members/me/farm-crops` | `getMyFarmCrops` |
| PUT | `/api/v1/members/me/profile` | `updateMyProfile` |
| GET | `/api/v1/members/{memberId}/profile` | `getPublicProfile` |
| GET | `/api/v1/pesticides` | `searchPesticides` |
| GET | `/api/v1/pesticides/{pesticideId}/pests` | `listPests` |
| GET | `/api/v1/policies/recommendations` | `listRecommendations` |
| GET | `/api/v1/policies/{policyProgramId}` | `getProgramDetail` |
| GET | `/api/v1/search` | `searchAll` |
| GET | `/api/v1/search/policies` | `searchPolicies` |
| GET | `/api/v1/search/posts` | `searchPosts` |
| GET | `/api/v1/search/records` | `searchRecords` |
| GET | `/api/v1/search/suggestions` | `suggestions` |
| GET | `/api/v1/test/me` | `getMyInfo` |
| GET | `/api/v1/test/ping` | `ping` |
| POST | `/api/v1/voice-sessions` | `createSession` |
| POST | `/api/v1/voice-sessions/{sessionId}/cancel` | `cancel` |
| POST | `/api/v1/voice-sessions/{sessionId}/confirm` | `confirm` |
| PATCH | `/api/v1/voice-sessions/{sessionId}/turns` | `submitTurns` |
| GET | `/api/v1/weather/daily` | `daily` |
| GET | `/api/v1/weather/detail` | `detail` |
| GET | `/api/v1/weather/home` | `home` |
| GET | `/api/v1/work-types` | `listWorkTypes` |

## Schemas

- `ApiError`
- `ApiResponseCancelledResponse`
- `ApiResponseCommentIdResponse`
- `ApiResponseCommentPageResponse`
- `ApiResponseConfirmedResponse`
- `ApiResponseCreatedResponse`
- `ApiResponseDailyResponse`
- `ApiResponseDetailResponse`
- `ApiResponseFarmResponse`
- `ApiResponseHomeResponse`
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
- `ApiResponseSearchAllResponse`
- `ApiResponseSearchPolicyPageResponse`
- `ApiResponseSearchPostPageResponse`
- `ApiResponseSearchRecordPageResponse`
- `ApiResponseStatusResponse`
- `ApiResponseSuggestionsResponse`
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
- `CategoryResponse`
- `CitationResponse`
- `CommentIdResponse`
- `CommentPageResponse`
- `CommentResponse`
- `CompleteOnboardingRequest`
- `ConditionResponse`
- `ConfirmedResponse`
- `CreateCommentRequest`
- `CreatedResponse`
- `CropOptionResponse`
- `CropProfileResponse`
- `CropResponse`
- `CurrentResponse`
- `DailyResponse`
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
- `FieldResponse`
- `ForecastResponse`
- `HarvestDetail`
- `HarvestDetailRequest`
- `HarvestDetailResponse`
- `HomeResponse`
- `ItemResponse`
- `KakaoLoginRequest`
- `LikeToggleResponse`
- `ListResponse`
- `LoginRequest`
- `LoginResponse`
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
- `PartialResponse`
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
- `SearchAllResponse`
- `SearchPolicyItemResponse`
- `SearchPolicyPageResponse`
- `SearchPolicySectionResponse`
- `SearchPostPageResponse`
- `SearchPostSectionResponse`
- `SearchRecordPageResponse`
- `SearchRecordSectionResponse`
- `SendVerificationCodeRequest`
- `SignUpRequest`
- `StatusResponse`
- `StructuredResultResponse`
- `SubmitTurnsRequest`
- `SuggestionsResponse`
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
