# 홈서버 RAG 운영 배포 설계

## 1. 목적

ChamChamCham 개발 서버에 코칭 RAG 실행 기반을 추가한다. 서버에서 PDF를 다시 임베딩하지 않고, 맥북 PostgreSQL에 이미 저장된 `TECH_DOCUMENT` 벡터만 선별 이전한다. 개발 서버 중단은 허용하며, 기존 PostgreSQL 데이터와 볼륨을 보존해 즉시 복구할 수 있어야 한다.

## 2. 확인된 현황

### 홈서버

- 호스트: Ubuntu 24.04.4 LTS, x86-64
- CPU: Intel N100 4코어, GPU 없음
- 메모리: 총 15.4GiB, 점검 당시 약 11GiB 가용
- 디스크: 약 328GB 가용
- 현재 PostgreSQL: `postgres:16-alpine`, `vector` 확장 설치 파일 없음
- 현재 DB 크기: 약 45MB
- OpenClaw: `agri-rag-coach` 에이전트 존재
- API와 OpenClaw gateway는 외부 Docker 네트워크 `npm-net`을 공유한다.
- OpenClaw gateway의 고정 네트워크 별칭은 `openclaw-gateway`다.

### 맥북 이전 원본

- 컨테이너: `chamchamcham-postgres`
- 데이터베이스: `chamchamchamdb`
- 테이블: `public.vector_store`
- 전체 벡터: 585건
- 이전 대상: `TECH_DOCUMENT` 580건
- 제외 대상: `FARMING_RECORD` 5건
- 원본 문서: 5개 PDF
- 벡터 차원: 전부 1024
- `cropName`: 전부 `GENERAL`
- `documentTitle`, `page`, `pdfPath` 누락: 0건

## 3. 선택한 접근

개발 서버를 한 번 중단하고 인프라, DB, 벡터 데이터, API를 순차 전환한다. 서버 CPU로 PDF를 재임베딩하거나 별도 벡터 전용 DataSource를 추가하지 않는다.

선택 이유:

- 이미 생성된 1024차원 `bge-m3` 벡터를 재사용할 수 있다.
- N100 CPU에서 580개 청크를 다시 임베딩하는 시간을 피한다.
- `FARMING_RECORD`를 제외한 문서 벡터만 명시적으로 이전할 수 있다.
- 기존 애플리케이션이 사용하는 Spring AI `PgVectorStore` 계약을 유지한다.

## 4. 목표 구성

```text
ChamChamCham API
├─ embedding -> http://ollama:11434
├─ vector search -> PostgreSQL public.vector_store
└─ coaching chat -> http://openclaw-gateway:18789
```

### Docker 서비스

#### API

- `default` 네트워크에서 PostgreSQL과 Ollama에 접근한다.
- `npm-net`에서 OpenClaw gateway에 접근한다.
- PostgreSQL과 Ollama healthcheck가 통과한 뒤 기동한다.

#### PostgreSQL

- pgvector가 포함된 PostgreSQL 16 이미지를 사용한다.
- 예시 기준 이미지는 `pgvector/pgvector:0.8.5-pg16`이며 실제 적용 시 digest까지 고정한다.
- 기존 Alpine 데이터 볼륨을 새 이미지에 직접 연결하지 않는다.
- 새 pgvector 전용 볼륨을 만들고 기존 DB logical dump를 복원한다.
- 기존 볼륨은 롤백을 위해 삭제하거나 수정하지 않는다.

#### Ollama

- 외부 호스트 포트를 공개하지 않는다.
- `default` 네트워크의 `ollama:11434`로만 제공한다.
- 모델 보존용 Docker 볼륨을 사용한다.
- `bge-m3` 한 모델만 설치하고 실제 모델 digest를 배포 기록에 남긴다.
- 초기 제한은 메모리 3~4GB, CPU 2코어, 동시 임베딩 1개로 둔다.

#### OpenClaw

- 기존 compose와 gateway를 변경하지 않는다.
- 이미 존재하는 `npm-net` 별칭 `openclaw-gateway`를 사용한다.
- gateway 인증키는 서버 `.env`에서만 제공한다.

## 5. 설정 책임

### 서버 `.env`

사용자가 배포 전에 `/home/wingwogus/apps/chamchamcham/.env`를 백업하고 다음 값을 추가한다. 실제 비밀값은 문서, Git, 로그에 기록하지 않는다.

```dotenv
OLLAMA_BASE_URL=http://ollama:11434
OPENCLAW_BASE_URL=http://openclaw-gateway:18789
OPENCLAW_API_KEY=<OpenClaw gateway token>
OPENCLAW_AGENT_ID=agri-rag-coach
RAG_EMBEDDING_MODEL=bge-m3
RAG_EMBEDDING_DIMENSION=1024
RAG_CHAT_MODEL=openclaw/agri-rag-coach
RAG_VECTOR_TABLE=vector_store
RAG_TIMEOUT_MILLIS=30000
```

- `.env` 권한은 `600`을 유지한다.
- 값을 추가한 뒤 인프라 준비가 끝나기 전에는 compose를 재기동하지 않는다.

### `application-prod.yml`

- 내부 URL을 하드코딩하지 않고 기존 환경변수 참조를 유지한다.
- 벡터 테이블을 `${RAG_VECTOR_TABLE:vector_store}`로 환경변수화한다.
- 운영에서 `schema-validation: true`를 적용한다.
- 차원 1024, cosine distance, HNSW 계약을 유지한다.

### `docker-compose.yml`

- PostgreSQL 이미지를 pgvector 포함 PG16으로 변경한다.
- 새 PostgreSQL 볼륨을 선언한다.
- Ollama 서비스, 영속 볼륨, healthcheck, 자원 제한을 추가한다.
- API가 PostgreSQL과 Ollama 준비 이후 기동하도록 의존성을 설정한다.
- Ollama는 외부 네트워크나 호스트 포트에 연결하지 않는다.

## 6. 데이터베이스 계약

현재 `backend/docs/db/rag-index-schema.sql`의 `rag_index_chunk`는 런타임이 읽는 테이블이 아니다. 배포 계약은 Spring AI 1.1.8의 `public.vector_store`로 통일한다.

필수 확장:

- `vector`
- `hstore`
- `uuid-ossp`

`vector_store` 필수 열:

- `id uuid primary key`
- `content text`
- `metadata json`
- `embedding vector(1024)`

인덱스는 `embedding vector_cosine_ops` 기반 HNSW를 사용한다. 새 피드백 도메인이 사용하는 다음 테이블의 운영 SQL도 같은 배포 전에 준비한다.

- `record_feedback`
- `record_feedback_next_action`
- `report_feedback`
- `report_feedback_item`
- `farming_cycle_report`

## 7. 배포 흐름

### 7.1 기존 서버 보호

1. 서버 compose 파일을 타임스탬프 백업한다.
2. 기존 PostgreSQL DB 전체를 custom-format logical dump로 백업한다.
3. dump SHA-256을 기록한다.
4. `pg_restore --list`가 성공해야 다음 단계로 진행한다.
5. API와 기존 PostgreSQL을 중단한다.

### 7.2 새 PostgreSQL 준비

1. 새 pgvector 이미지와 새 볼륨으로 PostgreSQL을 기동한다.
2. 기존 서버 DB dump를 복원한다.
3. 기존 주요 테이블 건수와 DB 크기를 이전 상태와 비교한다.
4. 필수 확장과 신규 스키마를 적용한다.
5. `vector_store.embedding` 차원이 1024인지 확인한다.

### 7.3 Ollama 준비

1. Ollama를 내부망 전용으로 기동한다.
2. `bge-m3`를 pull한다.
3. 맥북에서 문서 임베딩에 사용한 모델과 서버 모델의 digest가 일치하는지 확인한다.
4. 고정 문장을 임베딩해 결과가 1024차원인지 확인한다.
5. 맥북과 서버에서 같은 문장을 임베딩한 결과의 cosine similarity가 0.999 이상인지 확인한다.
6. digest 또는 임베딩 비교가 기준을 충족하지 못하면 벡터 이전을 중단한다.

### 7.4 TECH_DOCUMENT 이전

1. 맥북 `chamchamchamdb.public.vector_store`에서 `metadata->>'sourceType' = 'TECH_DOCUMENT'` 조건으로만 CSV를 만든다.
2. 추출 결과가 580건, 5문서, 전부 1024차원인지 재확인한다.
3. 파일 SHA-256을 기록하고 권한을 제한한다.
4. SCP로 서버의 임시 경로에 전송한다.
5. 서버의 unlogged 임시 테이블에 먼저 적재한다.
6. 다음 조건을 모두 검증한다.
   - 전체 580건
   - `FARMING_RECORD` 0건
   - 벡터 차원 1024
   - 필수 메타데이터 누락 0건
   - 서로 다른 문서 제목 5개
7. 검증 후 운영 `vector_store`에 병합한다.
8. 병합 후 HNSW 인덱스와 통계를 확인한다.

### 7.5 API 사전 검증과 전환

1. 외부 라우팅이 없는 임시 API 컨테이너로 먼저 기동한다.
2. PostgreSQL, Ollama, OpenClaw 내부 연결을 검증한다.
3. `참당귀 관수 재배 관리 약용작물`을 포함한 대표 질의로 `GENERAL` 문서가 검색되는지 확인한다.
4. 인용 문서명과 페이지를 확인한다.
5. 기록 피드백과 리포트 피드백의 구조화 응답 및 길이 계약을 확인한다.
6. 임시 컨테이너를 내린 뒤 정식 API를 기동한다.

## 8. 실패 처리와 롤백

- 백업 생성 또는 검증 실패: 작업을 시작하지 않는다.
- 새 DB 복원 실패: 새 볼륨을 사용하지 않고 기존 이미지와 기존 볼륨으로 복귀한다.
- 필수 확장 또는 스키마 실패: API를 기동하지 않는다.
- Ollama 모델 pull 또는 1024차원 검증 실패: 벡터 이전과 API 배포를 중단한다.
- 이전 건수, source type, 차원, 메타데이터 검증 실패: 운영 테이블에 병합하지 않는다.
- 임시 API 검증 실패: 기존 API, 기존 PostgreSQL 이미지, 기존 볼륨으로 복귀한다.
- 정식 API 기동 후 즉시 smoke test를 수행하며, 새로운 사용자 데이터가 기록되기 전에 롤백 여부를 결정한다.

롤백 시 pgvector 확장이 생성된 새 볼륨을 기존 `postgres:16-alpine`에 연결하지 않는다. 기존 이미지에는 기존 볼륨만 다시 연결한다.

## 9. 검증 기준

### 인프라

- PostgreSQL, Ollama, OpenClaw, API 컨테이너가 정상 상태다.
- API에서 `ollama`와 `openclaw-gateway` DNS가 해석된다.
- Ollama는 호스트 외부에 포트를 공개하지 않는다.
- 배포 후 호스트 가용 메모리가 3GB 이상 남는다.
- 지속적인 OOM 또는 CPU 포화가 없다.

### 벡터 데이터

- `TECH_DOCUMENT` 580건
- 5개 문서 제목
- `FARMING_RECORD` 0건
- 모든 embedding이 1024차원
- `sourceType`, `cropName`, `documentTitle`, `page`, `pdfPath` 누락 0건
- 맥북과 서버의 `bge-m3` digest 일치
- 고정 문장 임베딩 cosine similarity 0.999 이상

### 기능

- 대표 질의에서 문서가 검색된다.
- 인용 제목과 페이지가 실제 metadata와 일치한다.
- 기록 피드백 생성과 조회가 성공한다.
- 리포트 피드백 생성과 조회가 성공한다.
- 기록 피드백 23자·25자 목표와 리포트 코칭 최대 65자 계약이 유지된다.

## 10. 범위 제외

- 서버에서 PDF 재임베딩
- 공개 PDF 업로드 API
- 별도 벡터 전용 DataSource
- 기존 OpenClaw compose 또는 agent 변경
- PostgreSQL 기존 볼륨 삭제
- 운영 트래픽을 위한 GPU 확장 또는 고가용성 구성

## 11. 잔여 위험

- N100 CPU에서는 동시에 여러 임베딩 요청이 들어오면 응답이 느려질 수 있다.
- OpenClaw browser가 순간적으로 CPU 한 코어를 모두 사용할 수 있으므로 Ollama 동시성을 제한해야 한다.
- 맥북과 서버의 `bge-m3` 모델 digest가 다르면 동일한 벡터 공간을 보장할 수 없다.
- Spring AI `vector_store`와 기존 문서의 `rag_index_chunk` 계약 불일치는 구현 단계에서 반드시 제거해야 한다.
- 애플리케이션에 PDF 재인덱싱 기능이 없으므로 이후 문서 갱신은 별도 작업이 필요하다.
