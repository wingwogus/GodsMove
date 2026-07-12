# 가입 완료 / 추가 재배지 저장 계약 메모

- 작성일: 2026-07-11
- Figma evidence: [가입 완료 capture](2026-07-11-onboarding-completion.md)
- Figma node: `648:6728`
- Backend commit checked: `b75ba0e`
- Swagger snapshot SHA-256: `3cc2a1870dbc6006a9dd3591e7e1c1aee5bb188c4ac836c15d58657babdf2541`
- Status: Superseded on 2026-07-12. Backend proposal is to keep onboarding complete focused on the representative farm and add a standalone farm-add endpoint for extra farms.

## 2026-07-12 Update

Backend 제안:

> 온보딩 완료 API에 여러 재배지를 포함하기보다, 별도 farm 추가 endpoint를 만들면 마이페이지의 농지 추가/수정에도 재사용하기 좋다.

Frontend decision:

- Figma 흐름대로 온보딩 draft에서는 여러 재배지를 계속 받을 수 있게 한다.
- `POST /api/v1/auth/onboarding/complete`에는 첫 번째/대표 재배지만 보낸다.
- 두 번째 이후 재배지는 새 standalone farm-add endpoint가 생기면 순차 저장 또는 outbox 저장 흐름으로 연결한다.
- 따라서 이 문서의 기존 `CompleteOnboardingRequest.farms[]` 요청안은 보류한다.

## 2026-07-12 Main Recheck

- `main` 병합 이후 backend source와 deployed Swagger를 다시 확인했다.
- standalone farm create/update endpoint는 아직 없다.
- 현재 온보딩은 기존 백엔드 스펙에 맞춰 `POST /api/v1/auth/onboarding/complete`에 단일 대표 재배지만 전송한다.
- 두 번째 이후 재배지는 standalone farm endpoint가 추가될 때까지 local draft에만 남긴다.
- Swagger snapshot SHA-256: `46184ce3a531514753f11f8d995feef908d773eda2a62763ac7a1ba0f0fa81b5`
- 스펙 호환 메모: `FarmRequest.areaSqm`은 값이 있으면 양수여야 하므로, Step 2 수동 면적 입력과 최종 DTO에서 `0` 이하 값을 막는다.

## Product Intent Captured

`가입 완료` 화면에는 두 개의 CTA가 있다.

- `재배지 추가하기`: Step 2로 이동한다.
- `시작하기`: 회원가입 및 로그인 완료 상태로 Home으로 이동한다.

사용자 설명 기준으로, 추가 재배지는 단순 이름만 추가하는 것이 아니다. 재배지마다
Step 2의 위치·농지명 입력과 Step 3의 작물 선택을 다시 수행해야 한다.

즉, 온보딩 draft의 자연스러운 제품 모델은 다음 형태다.

```text
OnboardingDraft
├─ basic profile
└─ farms[]
   ├─ farm location/name
   └─ cropIds[]
```

## Current Backend Contract

최신 Swagger와 backend source 기준 `POST /api/v1/auth/onboarding/complete`는 단일
재배지만 받는다.

```text
CompleteOnboardingRequest
├─ name
├─ phone
├─ birthDate
├─ nickname
├─ experienceLevel
├─ managementType
├─ farm: FarmRequest
├─ cropIds: UUID[]
└─ profileMediaId?
```

backend `OnboardingService.complete`도 다음처럼 처리한다.

1. 요청 crop ids를 검증한다.
2. member onboarding profile을 완료 처리한다.
3. `command.farm` 하나로 farm 하나를 저장한다.
4. top-level `cropIds`를 그 farm의 `member_crop`으로 저장한다.

따라서 현재 complete endpoint만으로는 가입 완료 전 여러 재배지를 한 번에 저장할 수 없다.

## Related Existing Endpoint

최신 Swagger에는 `PUT /api/v1/members/me/profile`이 있고, 이 요청은
`farms: List<FarmRequest>`를 받는다. 이 endpoint는 가입 후 프로필 전체 수정에는 도움이 될
수 있다.

다만 `가입 완료` 화면의 `재배지 추가하기`를 Home 전 온보딩 루프로 해석하면,
이 endpoint만으로는 흐름이 깔끔하지 않다.

- `시작하기` 전인데 onboarding complete를 먼저 호출해야 할 수 있다.
- 첫 요청 성공 후 추가 재배지 저장 실패 시 사용자의 가입 상태와 재배지 draft 상태가 갈라진다.
- Home 진입 전 모든 재배지를 원자적으로 저장한다는 UX와 맞지 않는다.

## Current Frontend Contract

2026-07-12 구현 기준 frontend `OnboardingDraft`는 Figma 루프를 위해 여러 재배지를 표현한다.

```text
OnboardingDraft
├─ basic profile
├─ farms[]
│  ├─ cropIDs: [UUID]
│  ├─ farmName
│  ├─ farmRoadAddress
│  ├─ farmJibunAddress
│  ├─ farmLatitude / farmLongitude
│  ├─ farmPNU
│  ├─ farmLandCategory
│  ├─ farmAreaSqm
│  └─ farmAreaIsManualEntry
└─ activeFarmIndex
```

하위 호환을 위해 기존 `draft.farmName`, `draft.cropIDs` 등은 active farm accessor로 남긴다.
현재 `OnboardingCompleteRequestDTO`는 backend contract에 맞춰 첫 번째/대표 재배지만 다음으로 변환한다.

- `farm = FarmRequestDTO(farm: draft.representativeFarm)`
- `cropIds = draft.representativeFarm.cropIDs`
- `FarmRequestDTO.cropIds = draft.representativeFarm.cropIDs`

두 번째 이후 재배지는 새 standalone farm-add endpoint가 생기기 전까지 서버로 전송하지 않는다.

## Superseded Backend Request

아래 요청은 2026-07-11에 검토했던 `CompleteOnboardingRequest.farms[]` 확장안이다.
2026-07-12 backend 제안에 따라 이 방향은 보류한다.

Superseded 예상 요청 형태:

```json
{
  "name": "...",
  "phone": "...",
  "birthDate": "1990-01-01",
  "nickname": "...",
  "experienceLevel": 3,
  "managementType": "AGRICULTURAL_INDIVIDUAL",
  "farms": [
    {
      "name": "...",
      "roadAddress": "...",
      "jibunAddress": "...",
      "latitude": 37.0,
      "longitude": 127.0,
      "pnu": "...",
      "landCategory": "...",
      "areaSqm": 1234.5,
      "areaIsManualEntry": false,
      "boundaryCoordinates": [],
      "dataSource": {
        "address": "JUSO",
        "coordinate": "VWORLD",
        "parcel": "VWORLD",
        "landCharacteristic": "VWORLD"
      },
      "cropIds": ["..."]
    }
  ],
  "profileMediaId": "..."
}
```

Backend validation 후보:

- `farms`는 최소 1개
- 각 farm은 `name`, `roadAddress`, `latitude`, `longitude` 필수
- 각 farm의 `cropIds`는 최소 1개, 최대 5개
- 같은 요청 안에서 crop id 존재 여부 검증
- 저장은 member onboarding completion, farms, member_crop 전체가 하나의 transaction으로 처리

## Recommended Standalone Farm Endpoint Direction

Backend에 요청할 새 endpoint의 방향:

- 가입 완료 이후 또는 온보딩 완료 직후 같은 API로 농지 추가가 가능해야 한다.
- 마이페이지의 농지 추가/수정에서도 재사용할 수 있어야 한다.
- request body는 현재 `FarmRequest`에 준하되, farm별 `cropIds`를 함께 받을 수 있어야 한다.
- crop ids는 최소 1개, 최대 5개를 검증한다.
- 주소/좌표/필지/면적 필드는 현재 onboarding Step 2의 JUSO + V-World 흐름과 맞아야 한다.
- 실패 시 frontend가 draft/outbox를 유지하고 재시도할 수 있도록 idempotency 또는 client-generated id 지원 여부를 논의한다.

프론트 임시 정책:

- `시작하기` tap 시 현재 onboarding complete에는 대표 재배지만 보낸다.
- 추가 재배지는 draft 구조에 보존한다.
- standalone endpoint가 확정되면 `farms.dropFirst()`를 순차 저장하거나 outbox로 동기화한다.

## Frontend Implementation Implication

Frontend는 다음 모델이 필요하다.

- `OnboardingFarmDraft`
  - Step 2 fields: farm name, road/jibun address, coordinate, pnu, land category, area, boundary, data source
  - Step 3 fields: crop ids
- `OnboardingDraft.farms: [OnboardingFarmDraft]`
- `activeFarmIndex` 또는 editing state
- `재배지 추가하기` action
  - 빈 farm draft 추가
  - Step 2로 이동
  - Step 3 완료 후 completion screen으로 복귀
- 최종 제출 DTO
  - 현재는 대표 재배지 `single farm + cropIds`
  - 추가 재배지는 standalone farm endpoint 확정 후 별도 DTO 추가

## Remaining Decision Needed

- 새 farm 추가 endpoint의 path/method
- create와 update를 같은 endpoint로 처리할지 분리할지
- `cropIds` 위치: top-level vs farm body 내부
- 추가 재배지 저장 실패 시 Home 진입을 막을지, Home 진입 후 background sync/outbox로 처리할지
- idempotency key 또는 client-generated draft id 지원 여부
