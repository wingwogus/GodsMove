# 병해충/약제/비료 마스터데이터 — 실키 연동 후속 작업 (다음 세션용)

**목적:** PSIS 농약등록정보 API 키, 비료 관련 API 승인이 완료된 뒤 이어서 진행할 작업을 정리한다. 이 문서 작성 시점에는 두 키 모두 아직 없다.

## 지금까지 완료된 것 (병합 완료, `dev`에 반영됨)

- `Pesticide`/`Pest`/`PesticideApplication` 도메인 스키마
- `GET /api/v1/pesticides`(검색/커서), `GET /api/v1/pesticides/{id}/pests` 조회 API — PSIS 응답 형식과 무관하게 이미 완전히 동작함(우리 DB 스키마에만 의존)
- `PestControlRecord.pesticideName/pestTarget`(자유 텍스트) → `pesticideId`/`pestId` FK 전환
- PSIS 동기화 파이프라인 골격(`PsisPesticideHttpTransport`/`PsisPesticideResponseParser`/`PsisPesticideRowMapper`/`PesticideSyncService`) — `POST /api/v1/admin/pesticide-sync`
- 비료: **스키마도 API도 착수 안 함** (아래 "알아둘 것" 참고)

## 중요: 지금까지 확인한 것 중 "확정"과 "추정"을 구분할 것

작업을 이어받는 세션은 아래를 그대로 믿지 말고 재확인해야 한다.

- **확정(실물 데이터로 확인)**: PSIS 실검색화면(`https://psis.rda.go.kr/psis/agc/res/agchmRegistStusLst.ps`)에서 작물명·병해충명·품목명·상표명·희석배수 등이 포함된 실제 예시 행을 봤음(예: 만코제브 수화제 | 감자 | 역병 | 500배).
- **추정(문서 설명에서 추출, 미검증)**: PSIS "OpenAPI 이용안내" 페이지 설명에서 뽑아낸 필드명 후보 — `cropName`, `diseaseWeedName`(병해충명), `pestiKorName`(품목명), `pestiBrandName`(상표명) 등. `PsisPesticideRowMapper.kt`에 후보로 넣어뒀지만 실제 응답과 다를 수 있음.
- **폐기된 수치**: "137,877건"은 확정된 총 건수가 **아니다**. 같은 검색화면을 다른 시점/필터로 열어보면 126,659건/201건/183건 등으로 계속 바뀌었다 — 실시간 필터 결과 카운트일 뿐, 실제 규모는 **키로 첫 호출을 해서 응답의 총 건수 필드를 봐야 확정**된다.

## 다음 세션에서 할 일 — PSIS 농약등록정보 키 도착 시

1. `application-local.yml`(혹은 실제 프로필)에 `PSIS_PESTICIDE_BASE_URL`, `PSIS_PESTICIDE_SERVICE_KEY` 채우기.
2. **가장 먼저**: `numOfRows=10` 정도의 작은 페이지 크기로 딱 1번만 호출해서 실제 XML 응답을 눈으로 확인한다. 이때 확인할 것:
   - 실제 필드 태그명이 `PsisPesticideRowMapper.kt`의 후보 목록과 일치하는지 — 다르면 후보 목록 맨 앞에 실제 태그명 추가(이 파일만 고치면 됨, 구조 변경 불필요).
   - 응답에 총 건수(totalCount류) 필드가 있으면 그 값으로 실제 규모 확정.
3. 확정된 규모에 따라 동기화 방식 결정(아래 표).
4. `POST /api/v1/admin/pesticide-sync` 실행, 결과(`fetchedRowCount`/`createdApplicationCount`) 확인.

### 규모별 동기화 방식 결정 기준

| 실제 총 건수 | 방침 |
|---|---|
| 수백~수천 건 | 지금 코드(동기, 행별 개별 쿼리) 그대로 사용 — 몇 초~몇 십 초 내 끝나 타임아웃 위험 없음 |
| 수만 건 | `PolicySyncService`/`PolicySyncJob`/`PolicySyncAsyncRunner` 패턴을 그대로 참고해 비동기 실행 + 진행상황 추적 엔티티 추가 (`AdminPesticideSyncController`가 즉시 응답하고 별도 GET으로 상태 폴링) |
| 10만 건 이상 | 위 비동기+Job 추적에 더해, 행 단위 개별 쿼리(현재 `PesticideSyncService.upsertRows`)를 배치 upsert로 교체 필요 — 지금 방식 그대로면 쿼리 수가 건당 최대 6개라 10만 건대에서 매우 느려짐 |

참고: `PolicySyncService`는 "배치 처리"가 아니라 여전히 항목별 개별 쿼리를 쓴다 — 다만 그 데이터(NongupEZ 정책)가 수십~수백 건 규모라 문제가 안 드러났을 뿐이다. 진짜 배치/대량 upsert가 필요하면 이 프로젝트에 참고할 기존 패턴이 없으므로 새로 설계해야 한다.

## 비료 — 아직 손 안 댐, 사용자가 이미 "미리 준비" 요청했음

**중요**: 사용자는 애초에 비료도 농약처럼 "키만 넣으면 바로 되게 미리 준비"해달라고 명시적으로 요청했다. 이번 라운드에서는 그 요청을 놓치고 병해충/약제만 구현했다 — 다음 세션에서 이어받을 때 이 사실을 먼저 반영할 것.

비료는 근거가 약(pesticide보다 약함):
- 후보 API: `농촌진흥청_비료 품질검사`(data.go.kr/data/15101370) — 신청은 했으나 아직 승인 대기.
- **실제 검색화면이나 예시 데이터를 확인한 적이 없다** — data.go.kr 목록 페이지의 한 줄 설명("등록된 다양한 비료의 종류를 체계적으로 관리")만 있음. 이게 진짜 "비료 종류 목록"인지, 아니면 "품질검사 부적합 현황"류인지 불확실.

### 다음 세션 진행 순서

1. **승인 여부와 무관하게 지금 해도 되는 것**: `Fertilizer` 도메인 스키마(단순 flat 목록, N:M 매핑 없음) + `GET /api/v1/fertilizers` 조회 API. 이건 PSIS 응답 형식과 무관하게 항상 안전하다(Pesticide 조회 API와 동일한 이유).
2. **승인 후에만 할 것**: 실제 응답을 먼저 확인해서
   - "등록 비료 종류/품명" 데이터가 맞으면 → 병해충/약제와 동일한 패턴(HttpTransport/ResponseParser/RowMapper/SyncService)으로 시딩 파이프라인 구현.
   - 품질검사 결과(합격/불합격)만 있고 종류 목록이 없으면 → 마스터데이터화 포기, `FertilizingRecord.materialName`은 기존대로 자유 텍스트 유지.
3. 승인 전에 RowMapper급 필드 매핑을 미리 추측해서 만들지 않는다 — 근거(실제 화면/예시 데이터)가 없어서 pesticide보다 틀릴 확률이 높고, 다시 만들어야 할 가능성이 크다.

## 요약 체크리스트 (다음 세션 시작 시 이 순서로)

- [ ] PSIS 키 도착 확인
- [ ] 작은 페이지로 1회 시험 호출 → 실제 필드명/총 건수 확인
- [ ] `PsisPesticideRowMapper.kt` 태그명 보정
- [ ] 규모별 결정 기준표에 따라 동기/비동기/배치 여부 결정 (필요시 리팩터링)
- [ ] `POST /api/v1/admin/pesticide-sync` 실행 및 검증
- [ ] 비료 API 승인 확인
- [ ] (승인 전이라도) `Fertilizer` 스키마 + `GET /api/v1/fertilizers` 먼저 구현
- [ ] (승인 후) 실제 응답 확인 → 종류 목록 맞으면 시딩 파이프라인 구현, 아니면 자유 텍스트 유지로 종료
