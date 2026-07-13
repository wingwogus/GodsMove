# PSIS 농약등록정보 동기화 런북

## 전제

PSIS(농약안전정보시스템) 농약등록정보 API 키 발급이 완료된 상태를 전제로 한다.

- 엔드포인트: `GET http://psis.rda.go.kr/openApi/service.do`
- 파라미터: `apiKey`, `serviceCode=SVC01`, `serviceType=AA001`, `displayCount`(최대 50),
  `startPoint`(1-based 행 오프셋), 선택 필터(`cropName`/`pestiKorName`/`pestiBrandName`/
  `diseaseWeedName`/`useName`/`compName`)
- 성공 응답은 `<service><totalCount>...</totalCount><list><item>...</item>...</list></service>`
  형태이며 `<errorCode>` 태그가 없다.
- 실패 응답은 `<service><errorCode>ERR_101</errorCode><errorMsg>...</errorMsg></service>` 형태다.
  주요 코드: `ERR_101`(인증키 미등록), `ERR_102`(서비스 중지), `ERR_103`(잘못된 serviceCode),
  `ERR_201`(잘못된 파라미터), `ERR_901`(시스템 오류). `errorCode`가 하나라도 존재하면 실패다.
- 전체 데이터 규모는 약 143,912건이며, `displayCount=50` 기준 약 2,879회 호출이 필요하다.

## env 설정

`api` 모듈의 `application-{profile}.yml`이 다음 환경변수를 읽는다.

| 환경변수 | 설명 | 기본값 |
| --- | --- | --- |
| `PSIS_PESTICIDE_BASE_URL` | PSIS 실제 요청 URL(`http://psis.rda.go.kr/openApi/service.do`). | 없음 (미설정 시 요청 시점에 실패) |
| `PSIS_PESTICIDE_API_KEY` | PSIS에서 발급한 인코딩 API 키. 이중 인코딩 방지를 위해 그대로 전달된다. | 없음 |
| `PSIS_PESTICIDE_TIMEOUT_MILLIS` | HTTP 커넥션/요청 타임아웃(ms). 선택값. | `10000` |

## Step 1. 프로브

전량 동기화 전에 먼저 소량 데이터로 응답 형태와 매핑을 확인한다.

```http
POST /api/v1/admin/pesticide-sync/probe?rows=10
```

`rows`는 1~50 범위만 허용된다(PSIS `displayCount` 제약).

응답(`ApiResponse<PesticideProbeResponse>`)의 `data`에서 다음을 확인한다.

- `errorCode`/`errorMsg`: 성공이면 둘 다 `null`이어야 한다. `errorCode`가 값을 가지면 API
  키/URL 설정 문제일 가능성이 크며, 컨트롤러가 아니라 서비스 단에서
  `BusinessException(PESTICIDE_SYNC_FAILED)`로 502가 반환되므로 프로브 응답 자체가 실패
  응답으로 온다.
- `totalCount`: 실제 데이터 규모(정상이면 143,912 부근).
- `distinctTagNames`: 실응답에 존재하는 실제 XML 태그명 목록.
- `requiredKeyResolution`: `itemName`/`cropName`/`pestName` 각각이 매핑됐는지 여부.
- `mapped`: 위 세 필드까지 모두 해석됐을 때만 non-null이다.

## Step 2. 전량 동기화(비동기 잡)

143k건 전체 순회는 시간이 걸리므로 요청-응답을 동기로 기다리지 않는다. 잡을 생성하면
`RUNNING` 상태로 즉시 응답하고, 실제 페이지네이션 순회는 서버가 비동기로 수행한다.

```http
POST /api/v1/admin/pesticide-sync
```

응답(`ApiResponse<PesticideSyncJobSummaryResponse>`)에서 `jobId`와 `status`(`RUNNING`)를 받는다.

```http
GET /api/v1/admin/pesticide-sync/{jobId}
```

`status`가 `SUCCEEDED`가 될 때까지 폴링한다. `ApiResponse<PesticideSyncJobDetailResponse>`의
`totalCount`/`fetchedRowCount`/`createdApplicationCount`를 확인한다. 이미 동기화된 데이터에
재실행하면 dedup 로직 때문에 `createdApplicationCount`가 낮게(또는 0으로) 나오는 것이 정상이다.
실패 시 `status`는 `FAILED`가 되고 `errorMessage`에 원인이 담긴다.

## 롤백

현재 pesticide/pest/pesticide_application/pesticide_sync_job 테이블에 실데이터가 없다는 전제로
진행한다. 문제가 발견되면 네 테이블을 truncate한 뒤 Step 1부터 다시 실행한다.

## dev/prod 스키마 준비

dev/prod는 `ddl-auto: none`이고 Flyway가 없으므로, 이번 변경으로 추가된
`pesticide_sync_job` 테이블(및 관련 컬럼)을 배포 전에 수동으로 준비해야 한다. 로컬에서
`ddl-auto: create`로 생성한 DDL을 참고해 dev/prod 스키마에 반영한다.
