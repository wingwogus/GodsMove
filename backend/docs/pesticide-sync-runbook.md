# PSIS 농약등록정보 동기화 런북

## 전제

PSIS(농약안전정보시스템) 농약등록정보 API 키 발급이 완료된 상태를 전제로 한다.

## env 설정

`api` 모듈의 `application-{profile}.yml`이 다음 환경변수를 읽는다.

| 환경변수 | 설명 | 기본값 |
| --- | --- | --- |
| `PSIS_PESTICIDE_BASE_URL` | PSIS 마이페이지에 표시되는 실제 요청 URL. 현재 값은 placeholder이므로 발급된 실제 URL로 교체해야 한다. | 없음 (미설정 시 요청 시점에 실패) |
| `PSIS_PESTICIDE_SERVICE_KEY` | 공공데이터포털에서 발급한 인코딩 서비스키. 이중 인코딩 방지를 위해 그대로 전달된다. | 없음 |
| `PSIS_PESTICIDE_TIMEOUT_MILLIS` | HTTP 커넥션/요청 타임아웃(ms). 선택값. | `10000` |

## Step 1. 프로브

앱 기동 후, 전량 동기화 전에 먼저 소량 데이터로 응답 형태를 확인한다.

```http
POST /api/v1/admin/pesticide-sync/probe?rows=10
```

응답(`ApiResponse<PesticideProbeResponse>`)의 `data`에서 다음을 확인한다.

- `resultCode`: 성공이면 `"00"`이어야 한다. 그 외 값이면 서비스키/URL 설정 문제일 가능성이 크므로
  `resultMsg`를 참고해 원인을 파악한다. (`resultCode`가 에러 값이면 컨트롤러가 아니라 서비스 단에서
  `BusinessException(PESTICIDE_SYNC_FAILED)`로 502가 반환되므로, 프로브 응답 자체가 실패 응답으로 온다.)
- `totalCount`: 실제 데이터 규모.
- `distinctTagNames`: 실응답에 존재하는 실제 XML 태그명 목록. `PsisPesticideRowMapper`의 후보
  태그 목록과 비교해 실제 태그가 후보에 포함되어 있는지 확인한다.
- `requiredKeyResolution`: `itemName`/`cropName`/`pestName` 각각이 매핑됐는지 여부.
- `mapped`: 위 세 필드까지 모두 해석됐을 때만 non-null이다. `null`이면 매핑 실패다.

## Step 2. 보정

`mapped`가 `null`이거나 `requiredKeyResolution`에 `false`가 있으면, 실패한 필드의 실제 태그명을
`PsisPesticideRowMapper`의 해당 후보 리스트(`ITEM_NAME_KEYS`, `CROP_NAME_KEYS`, `PEST_NAME_KEYS` 등)
맨 앞에 추가한다. 이 파일만 수정하면 되고 구조 변경은 필요 없다. 수정 후 Step 1을 다시 실행해
`mapped`가 채워지는지 재확인한다.

## Step 3. 규모 판단

- `totalCount`가 수천 건 이하면 현행 전량(full) 동기화 그대로 사용해도 된다.
- `totalCount`가 수만 건 이상이면 현재 행 단위 upsert(`PesticideSyncService.sync`)가 느릴 수 있다.
  이 경우 `PolicySyncJob` 패턴처럼 비동기 실행 + 배치 upsert로 전환하는 별도 작업이 필요하지만,
  이번 작업 범위에는 포함되지 않는다(YAGNI — 실제 규모를 확인한 뒤 필요하면 요청한다).
- 만약 `type=xml` 파라미터로 XML 응답이 오지 않는다면(JSON 등으로 응답), PSIS API의 실제 파라미터명이
  `dataType`, `_type` 등 다른 이름일 수 있다. `PsisPesticideHttpTransport.get()`에 전달하는 쿼리
  파라미터(`sync`/`probe` 양쪽에서 사용하는 `pageNo`/`numOfRows`/`type`)를 조정 지점으로 삼는다.

## Step 4. 전량 동기화

프로브로 매핑이 정상 확인된 뒤 전량 동기화를 실행한다.

```http
POST /api/v1/admin/pesticide-sync
```

응답의 `fetchedRowCount`(가져온 원본 행 수)와 `createdApplicationCount`(새로 생성된
PesticideApplication 수)를 확인한다. 이미 동기화된 데이터에 대해 재실행하면 dedup 로직 때문에
`createdApplicationCount`가 0이 되는 것이 정상이다.

## 롤백

현재 pesticide/pest/pesticide_application 테이블에 실데이터가 없다는 전제로 진행한다. 문제가
발견되면 세 테이블을 truncate한 뒤 Step 1부터 다시 실행한다.
