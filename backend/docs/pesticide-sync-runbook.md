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
실패 시 `status`는 `FAILED`가 되고 `errorMessage`에 원인이 담긴다. `fetchedRowCount`가
`totalCount`에 미달하면(중간 빈 페이지 등) 잡은 자동으로 `FAILED` 처리되므로 그대로 재실행한다.

> ⚠️ `/api/v1/admin/**` 경로는 `ROLE_ADMIN` 권한이 필요하다(SecurityConfig). 아직 관리자 권한
> 부여 절차가 없다면 아래 "대안"으로 적재하고, 관리자 인증은 추후 도입한다.

### 대안: 관리자 토큰 없이 수동 로더로 적재 (현재 권장)

HTTP·토큰 없이 실 DB에 직접 적재하는 1회성 로더 테스트가 있다:
`api/src/test/kotlin/com/chamchamcham/api/pesticide/PesticideSyncManualLoaderTest.kt`.

일반 `./gradlew test`에서는 절대 실행되지 않고, 전용 플래그 `PSIS_PESTICIDE_SYNC_RUN=true`가
있을 때만 활성화된다(로컬 Postgres + Redis 기동 필요 — 로컬 앱 구동과 동일 전제).

```bash
PSIS_PESTICIDE_SYNC_RUN=true \
PSIS_PESTICIDE_API_KEY=<서비스인증키> \
PSIS_PESTICIDE_BASE_URL=http://psis.rda.go.kr/openApi/service.do \
./gradlew :api:test --tests "com.chamchamcham.api.pesticide.PesticideSyncManualLoaderTest"
```

`runExistingJob`을 동기로 끝까지 돌리므로 전량 적재가 끝날 때까지 블로킹된다(수 분~수십 분).
콘솔의 `[PSIS sync] status=... total=... fetched=... createdApplications=...` 로그로 결과를
확인한다. `SUCCEEDED`가 아니면 단언에서 실패로 드러난다.

적재 직후 같은 실행 안에서 `PesticideCatalogService`(검색/병해충 조회)까지 함께 검증하며,
`[verify] ...` 로그로 적재 건수와 검색·병해충 조회 결과를 확인한다.

> ⚠️ local 프로필은 `ddl-auto: create`라 **컨텍스트가 부팅될 때마다 스키마를 DROP/재생성**한다.
> 적재가 끝난 뒤 별도 테스트나 앱을 local 프로필로 다시 띄우면 방금 넣은 데이터가 통째로 지워진다.
> 그래서 적재와 조회 검증을 하나의 테스트(=한 번의 부팅) 안에서 끝낸다. 실데이터를 계속 쓰려면
> dev/prod처럼 `ddl-auto: none`으로 스키마를 미리 준비한 환경에 적재해야 한다.

## 롤백

현재 pesticide/pest/pesticide_application/pesticide_sync_job 테이블에 실데이터가 없다는 전제로
진행한다. 문제가 발견되면 네 테이블을 truncate한 뒤 Step 1부터 다시 실행한다.

## 서버(dev/prod) 반영 — dump/restore 핸드오프

dev/prod는 `ddl-auto: none`이고 Flyway가 없다. 즉 앱이 부팅해도 테이블을 스스로 만들지
않으므로, **농약 4개 테이블(스키마)과 카탈로그 데이터(약 14만 건)를 서버 DB에 직접 넣어줘야**
한다. 서버에서 PSIS API를 다시 호출(수천 회, ~1시간)하는 대신, **로컬에 이미 적재·검증한
데이터를 그대로 덤프해 서버에 붓는다.** `pg_dump`가 만든 파일 하나에 `CREATE TABLE`(스키마)
+ 인덱스 + 외래키 + 데이터가 모두 들어가므로, 별도의 DDL 스크립트는 필요 없다.

> 대상 테이블: `pesticide`, `pest`, `pesticide_application`(← 두 테이블을 참조하는 외래키 보유),
> `pesticide_sync_job`(잡 이력용, 스키마만 필요하고 데이터는 옮기지 않음). 이 4개는 서로만
> 참조하고 `member` 등 다른 테이블과 얽히지 않으므로 이 묶음만으로 완결적이다.

작업은 두 사람으로 나뉜다. **Part A는 이 앱을 빌드/실행하는 사람(=덤프 파일 제작)**, **Part B는
서버 DB 접근 권한을 가진 사람(=서버에 적용)**. Part B는 이 대화 맥락 없이도 단독으로 수행 가능하도록
아래에 모든 명령을 적어 둔다.

### Part A. 덤프 파일 만들기 (앱 담당자, 로컬)

전제: 로컬 Postgres(도커 컨테이너 `ccc-postgres`, 5444)에 `PesticideSyncManualLoaderTest`로
데이터가 적재되어 있고 `[verify]` 단언이 통과한 상태.

```bash
docker exec ccc-postgres pg_dump -U chamchamcham -d chamchamchamdb \
  -t pesticide -t pest -t pesticide_application -t pesticide_sync_job \
  --exclude-table-data=pesticide_sync_job \
  --clean --if-exists --no-owner --no-privileges \
  > pesticide-seed.sql
```

- `--exclude-table-data=pesticide_sync_job`: 잡 이력 테이블은 **구조만** 만들고 로컬 잡 로그는
  옮기지 않는다.
- `--clean --if-exists`: 파일 맨 앞에 `DROP TABLE IF EXISTS ...`가 붙어, 서버에서 **다시 적용해도**
  깨지지 않는다(기존 4개 테이블을 지우고 새로 만든다 — 서버에 이 데이터 외 보존할 것이 없다는 전제).
- `--no-owner --no-privileges`: 로컬 롤/권한 구문을 빼서 서버 계정에 그대로 적용되게 한다.

만들어진 파일을 확인한다(사람 눈으로 첫 줄과 데이터량 정도만).

```bash
ls -lh pesticide-seed.sql          # 파일 크기(수십 MB 수준이면 정상)
grep -c "^COPY " pesticide-seed.sql # COPY 블록이 3개(pesticide/pest/application) 보이면 정상
```

이 `pesticide-seed.sql` 파일을 Part B 담당자에게 전달한다(사내 스토리지/첨부 등). **파일에 비밀값은
없다**(공개 카탈로그 데이터 + 스키마뿐, API 키/토큰 없음).

### Part B. 서버 DB에 적용 (서버 담당자) — 처음 하는 사람용 전체 절차

받는 것: `pesticide-seed.sql` 파일 하나. 필요한 것: 서버 PostgreSQL 접속 정보와 `psql` 클라이언트.
**dev에 먼저 적용해 확인한 뒤 prod에 적용한다.**

1. **psql 준비 확인** — 로컬에 psql이 없으면 설치한다(macOS `brew install libpq`, Ubuntu
   `apt-get install postgresql-client`). 서버 DB로 나가는 네트워크(방화벽/VPN)가 열려 있어야 한다.

2. **접속 확인** — 접속 문자열을 환경변수로 둔다(히스토리에 비밀번호가 남지 않게 `read`로 입력).
   `<...>`는 실제 값으로 바꾼다.

   ```bash
   read -s -p "DB URL 입력: " DBURL; echo
   # 형식 예: postgresql://<사용자>:<비밀번호>@<호스트>:<포트>/<DB이름>
   psql "$DBURL" -c "select version();"   # 버전 문구가 출력되면 접속 성공
   ```

3. **(안전장치) 현재 상태 백업** — 만약 이미 같은 테이블이 있다면 먼저 백업한다. 없으면 이 단계는
   에러 없이 빈 결과가 나오니 넘어가도 된다.

   ```bash
   pg_dump "$DBURL" -t pesticide -t pest -t pesticide_application -t pesticide_sync_job \
     --no-owner --no-privileges > server-pesticide-backup-$(date +%Y%m%d).sql 2>/dev/null || true
   ```

4. **적용** — 오류가 나면 즉시 멈추도록 `ON_ERROR_STOP=1`을 준다.

   ```bash
   psql "$DBURL" -v ON_ERROR_STOP=1 -f pesticide-seed.sql
   ```

   중간에 멈추지 않고 끝까지 돌면 성공이다. `--clean` 덕분에 재실행해도 된다.

5. **검증** — 건수가 0이 아니고, 조인이 맞물리는지 확인한다.

   ```bash
   psql "$DBURL" -c "
     select 'pesticide' t, count(*) from pesticide
     union all select 'pest', count(*) from pest
     union all select 'pesticide_application', count(*) from pesticide_application;"
   ```

   `pesticide`/`pest`가 수천~수만, `pesticide_application`이 10만 안팎이면 정상이다(로컬 적재 시점의
   `[verify]` 건수와 일치해야 한다).

6. **앱 반영** — dev/prod 앱은 `ddl-auto: none`이라 이 테이블들을 건드리지 않는다. 이미 떠 있으면
   재시작 없이도 조회 API(`GET /api/v1/pesticides`, `.../{id}/pests`)가 바로 실데이터를 반환한다.
   단, **서버에 배포된 앱 빌드가 이번 농약 기능(엔티티)을 포함**하고 있어야 한다.

7. **dev 확인 후 prod 반복** — 3~5단계를 prod 접속 문자열로 한 번 더 수행한다.

**롤백**: 문제가 생기면 4단계 파일을 다시 적용하거나(멱등), 3단계 백업 파일을 `psql "$DBURL" -f
server-pesticide-backup-YYYYMMDD.sql`로 되돌린다. 완전히 비우려면
`truncate pesticide_application, pest, pesticide restart identity cascade;`.

> ⛔ **절대 금지**: 이 서버 DB를 로컬 앱/로더가 `local` 프로필(`ddl-auto: create`)로 바라보게 하면
> 부팅 순간 스키마째 삭제된다. 로더는 로컬 DB 전용이다.

### 이 라운드의 다른 스키마 변경 (참고)

Phase 1 기록 항목 개선(`feat/farming-record-refine` 브랜치)은 기존 farming 테이블의 컬럼을
바꾼다(신규 `planting_method` 등 enum/단위 변경). 이는 옮길 데이터가 없어 덤프가 아니라 해당 PR의
배포노트대로 스키마를 갱신해야 하며, **농약 반영과는 별개**다.
