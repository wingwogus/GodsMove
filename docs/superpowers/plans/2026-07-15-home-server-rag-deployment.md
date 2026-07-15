# Home Server RAG Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the MacBook's 580 `TECH_DOCUMENT` vectors to the ChamChamCham home server and run production coaching RAG against pgvector, internal Ollama `bge-m3`, and the existing internal OpenClaw gateway without risking the current PostgreSQL volume.

**Architecture:** Keep the API's Spring AI `PgVectorStore` contract on `public.vector_store`, replace the server PostgreSQL container with a digest-pinned pgvector PG16 image backed by a new volume, and add a digest-pinned internal-only Ollama service. The one-time migration uses a verified logical backup/restore and a staged CSV import; the automatic `main` deployment is gated until a server-side `rag-v1` readiness marker is created after candidate API verification.

**Tech Stack:** Kotlin 2.2.21, Spring Boot 3.5.4, Spring AI 1.1.8, PostgreSQL 16, pgvector 0.8.5, Ollama 0.30.8, Docker Compose v2, GitHub Actions, Bash, `psql`, `pg_dump`, `jq`

## Global Constraints

- Preserve the domain term `member`; do not introduce project-owned `user` naming.
- Add no new application or build dependencies.
- Keep `spring.ai.vectorstore.pgvector.initialize-schema: false`; production schema creation remains an explicit reviewed operation.
- Keep the vector contract exactly `public.vector_store`, `vector(1024)`, cosine distance, and HNSW.
- Import only rows where `metadata->>'sourceType' = 'TECH_DOCUMENT'`; the expected result is exactly 580 rows from 5 document titles and zero `FARMING_RECORD` rows.
- Do not re-embed PDFs on the server and do not add a public PDF upload endpoint or a second vector DataSource.
- Do not attach the existing Alpine PostgreSQL data volume to the pgvector Debian image; retain the old volume untouched for rollback.
- Use `pgvector/pgvector:0.8.5-pg16@sha256:1d533553fefe4f12e5d80c7b80622ba0c382abb5758856f52983d8789179f0fb`.
- Use `ollama/ollama:0.30.8@sha256:05b6fe5143ed006d6d4abd39bdd575f962a5822bdf81e6fbb5e6894eb984ab9c`.
- Expose no Ollama host port and attach Ollama only to the Compose default network.
- Limit Ollama to 4 GiB RAM, 2 CPUs, one parallel request, and one loaded model.
- Keep OpenClaw unchanged and reach it through `http://openclaw-gateway:18789` on external network `npm-net`.
- Never print, commit, copy off-server, or place in shared `/tmp` output any `.env` value, gateway key, JWT, database password, or raw credential.
- Keep the server `.env` mode at `600`; the user owns its secret values.
- The first `main` build must publish the API image but must not replace server Compose until `/home/wingwogus/apps/chamchamcham/.rag-infrastructure-ready` contains exactly `rag-v1`.
- Preserve record-feedback copy targets (good point 15–23 characters, next actions 15–25 characters) and report-feedback limits (all public sections 20–65 characters); this deployment must not relax them.
- Do not touch the untracked `.claude/` directory.
- Every repository commit must use Conventional Commits plus the Lore trailers required by `AGENTS.md`.

---

## File Map

- Create `backend/api/src/test/kotlin/com/chamchamcham/config/ProductionRagConfigurationTest.kt` — locks the production vector table override and schema-validation behavior.
- Modify `backend/api/src/main/resources/application-prod.yml` — consumes `RAG_VECTOR_TABLE` and validates the manually managed pgvector schema.
- Modify `.env.example` — documents non-secret production RAG environment keys and internal URLs.
- Modify `docker-compose.yml` — defines digest-pinned pgvector/Ollama services, new volumes, health checks, and resource limits.
- Modify `.github/workflows/deploy.yml` — adds manual rerun support and prevents the one-time migration from being bypassed by automatic deployment.
- Replace `backend/docs/db/rag-index-schema.sql` — defines Spring AI's actual `public.vector_store` schema for TECH_DOCUMENT vectors.
- Create `backend/docs/db/coaching-feedback-schema.sql` — defines the four manually managed record/report feedback tables.
- Create `backend/docs/db/rag-deployment-verify.sql` — fails deployment when extensions, tables, columns, constraints, or HNSW index do not match the runtime contract.
- Modify `backend/api/src/test/kotlin/com/chamchamcham/api/coaching/recordfeedback/RecordFeedbackGenerationVectorStoreSmokeTest.kt` — removes the stale inactive seed-endpoint instruction.

## Stop Conditions

Stop immediately without moving to the next task when any of these occurs:

- A backup cannot be listed with `pg_restore --list`.
- The new PostgreSQL container mounts `chamchamcham_postgres-data` instead of `chamchamcham_postgres-pgvector-data`.
- Local and server `bge-m3` model digests differ.
- The fixed-sentence embedding is not 1024-dimensional or cosine similarity is below `0.999`.
- The staged import is not exactly 580 `TECH_DOCUMENT` rows from 5 document titles with complete required metadata.
- Schema verification, candidate API startup, authenticated RAG query, feedback generation, or copy-length verification fails.
- Available host memory falls below 3 GiB or a container is OOM-killed.

---

### Task 1: Lock the production RAG configuration contract

**Files:**
- Create: `backend/api/src/test/kotlin/com/chamchamcham/config/ProductionRagConfigurationTest.kt`
- Modify: `backend/api/src/main/resources/application-prod.yml:37-54`
- Modify: `.env.example:25-26`

**Interfaces:**
- Consumes: Spring Boot's existing `YamlPropertySourceLoader` test pattern.
- Produces: `spring.ai.vectorstore.pgvector.table-name=${RAG_VECTOR_TABLE:vector_store}` and `schema-validation=true`; the Compose/API environment names used in later tasks.

- [ ] **Step 1: Write the failing production YAML test**

Create `backend/api/src/test/kotlin/com/chamchamcham/config/ProductionRagConfigurationTest.kt` with exactly:

```kotlin
package com.chamchamcham.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertySourcesPropertyResolver
import org.springframework.core.io.ClassPathResource

class ProductionRagConfigurationTest {
    private val productionPropertySources =
        YamlPropertySourceLoader().load(
            "application-prod.yml",
            ClassPathResource("application-prod.yml"),
        )

    @Test
    fun `production vector table defaults to Spring AI vector store`() {
        assertEquals("vector_store", resolver().getProperty("spring.ai.vectorstore.pgvector.table-name"))
    }

    @Test
    fun `production vector table can be overridden by environment`() {
        val resolver = resolver(MapPropertySource("ragOverride", mapOf("RAG_VECTOR_TABLE" to "vector_store_test")))

        assertEquals("vector_store_test", resolver.getProperty("spring.ai.vectorstore.pgvector.table-name"))
    }

    @Test
    fun `production validates the manually managed vector schema`() {
        assertEquals(
            true,
            resolver().getProperty("spring.ai.vectorstore.pgvector.schema-validation", Boolean::class.java),
        )
    }

    private fun resolver(override: MapPropertySource? = null): PropertySourcesPropertyResolver {
        val propertySources = MutablePropertySources()
        override?.let(propertySources::addFirst)
        productionPropertySources.forEach(propertySources::addLast)
        return PropertySourcesPropertyResolver(propertySources)
    }
}
```

- [ ] **Step 2: Run the focused test and confirm the red state**

Run from `backend/`:

```bash
./gradlew :api:test --tests 'com.chamchamcham.config.ProductionRagConfigurationTest'
```

Expected: FAIL. The override test still resolves `vector_store`, and the schema-validation test resolves `null`.

- [ ] **Step 3: Apply the minimal production configuration**

Replace only the pgvector block in `backend/api/src/main/resources/application-prod.yml` with:

```yaml
    vectorstore:
      pgvector:
        initialize-schema: false
        schema-validation: true
        schema-name: public
        table-name: ${RAG_VECTOR_TABLE:vector_store}
        dimensions: ${RAG_EMBEDDING_DIMENSION:1024}
        distance-type: COSINE_DISTANCE
        index-type: HNSW
```

Append this non-secret block to `.env.example` after `OPENAI_API_KEY`:

```dotenv

# Coaching RAG (Docker internal service URLs)
OLLAMA_BASE_URL=http://ollama:11434
OPENCLAW_BASE_URL=http://openclaw-gateway:18789
OPENCLAW_API_KEY=replace-with-openclaw-gateway-token
OPENCLAW_AGENT_ID=agri-rag-coach
RAG_EMBEDDING_MODEL=bge-m3
RAG_EMBEDDING_DIMENSION=1024
RAG_CHAT_MODEL=openclaw/agri-rag-coach
RAG_VECTOR_TABLE=vector_store
OPENCLAW_CONNECT_TIMEOUT_MILLIS=3000
OPENCLAW_READ_TIMEOUT_MILLIS=30000
```

- [ ] **Step 4: Run the focused test and configuration search**

Run:

```bash
./gradlew :api:test --tests 'com.chamchamcham.config.ProductionRagConfigurationTest'
rg -n 'schema-validation|RAG_VECTOR_TABLE|OLLAMA_BASE_URL|OPENCLAW_BASE_URL' api/src/main/resources/application-prod.yml ../.env.example
```

Expected: Gradle `BUILD SUCCESSFUL`; search shows `schema-validation: true`, `${RAG_VECTOR_TABLE:vector_store}`, and only environment-driven internal URLs.

- [ ] **Step 5: Commit the configuration contract**

Run from the repository root:

```bash
git add .env.example backend/api/src/main/resources/application-prod.yml backend/api/src/test/kotlin/com/chamchamcham/config/ProductionRagConfigurationTest.kt
git commit \
  -m "fix(rag): 운영 벡터 설정 계약 강화" \
  -m "수동 pgvector 스키마를 시작 시 검증하고 배포 환경에서 벡터 테이블 이름을 명시적으로 주입할 수 있게 한다." \
  -m $'Constraint: 운영 스키마 자동 생성은 비활성 상태를 유지해야 함\nConfidence: high\nScope-risk: narrow\nDirective: RAG_VECTOR_TABLE 기본값은 Spring AI 실제 테이블인 vector_store로 유지할 것\nTested: ./gradlew :api:test --tests com.chamchamcham.config.ProductionRagConfigurationTest\nNot-tested: 홈서버 컨테이너 연결은 배포 단계에서 검증'
```

Expected: one commit containing only the three listed files.

---

### Task 2: Define safe pgvector/Ollama Compose and gate automatic deployment

**Files:**
- Modify: `docker-compose.yml:1-39`
- Modify: `.github/workflows/deploy.yml:1-62`

**Interfaces:**
- Consumes: Task 1's `.env` names and external `npm-net`; existing image `vantagac/chamchamcham-api:latest`.
- Produces: services `postgres`, `ollama`, and `api`; volumes `postgres-pgvector-data` and `ollama-data`; readiness marker contract `rag-v1`.

- [ ] **Step 1: Run a failing Compose contract assertion against the current file**

Run from the repository root:

```bash
docker compose --env-file .env.example config --format json | jq -e '
  .services.postgres.image == "pgvector/pgvector:0.8.5-pg16@sha256:1d533553fefe4f12e5d80c7b80622ba0c382abb5758856f52983d8789179f0fb"
  and .services.ollama.image == "ollama/ollama:0.30.8@sha256:05b6fe5143ed006d6d4abd39bdd575f962a5822bdf81e6fbb5e6894eb984ab9c"
  and (.services.ollama.ports == null)
  and (.services.api.depends_on.ollama.condition == "service_healthy")
'
```

Expected: non-zero exit; the current Compose has no `ollama` service and uses `postgres:16-alpine`.

- [ ] **Step 2: Replace `docker-compose.yml` with the complete target configuration**

Use exactly:

```yaml
name: chamchamcham

services:
  api:
    image: vantagac/chamchamcham-api:latest
    container_name: chamchamcham-api
    restart: unless-stopped
    env_file:
      - .env
    expose:
      - "8080"
    depends_on:
      postgres:
        condition: service_healthy
      ollama:
        condition: service_healthy
    networks:
      - default
      - npm-net

  postgres:
    image: pgvector/pgvector:0.8.5-pg16@sha256:1d533553fefe4f12e5d80c7b80622ba0c382abb5758856f52983d8789179f0fb
    container_name: chamchamcham-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB:?POSTGRES_DB is required}
      POSTGRES_USER: ${POSTGRES_USER:?POSTGRES_USER is required}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}
    volumes:
      - postgres-pgvector-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U \"$${POSTGRES_USER}\" -d \"$${POSTGRES_DB}\""]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 20s

  ollama:
    image: ollama/ollama:0.30.8@sha256:05b6fe5143ed006d6d4abd39bdd575f962a5822bdf81e6fbb5e6894eb984ab9c
    container_name: chamchamcham-ollama
    restart: unless-stopped
    environment:
      OLLAMA_HOST: 0.0.0.0:11434
      OLLAMA_NUM_PARALLEL: "1"
      OLLAMA_MAX_LOADED_MODELS: "1"
      OLLAMA_KEEP_ALIVE: 5m
    volumes:
      - ollama-data:/root/.ollama
    expose:
      - "11434"
    healthcheck:
      test: ["CMD", "ollama", "list"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 20s
    mem_limit: 4g
    cpus: 2.0
    networks:
      - default

volumes:
  postgres-pgvector-data:
  ollama-data:

networks:
  npm-net:
    external: true
```

- [ ] **Step 3: Add a one-time infrastructure gate to `.github/workflows/deploy.yml`**

Add `workflow_dispatch:` beside the existing push trigger:

```yaml
on:
  push:
    branches:
      - main
  workflow_dispatch:
```

Inside the existing `Deploy over SSH` script, insert this block immediately after `ssh_opts` is defined and before the first `mkdir`/`scp` command:

```bash
          if ! ssh "${ssh_opts[@]}" "$DEPLOY_TARGET" \
            "test -f '$DEPLOY_PATH/.rag-infrastructure-ready' && grep -Fxq 'rag-v1' '$DEPLOY_PATH/.rag-infrastructure-ready'"; then
            echo "::notice::API image was published, but server deploy was skipped until the one-time rag-v1 migration is verified."
            exit 0
          fi
```

Do not move `scp docker-compose.yml` above this gate. This guarantees the first `main` run publishes `latest` but leaves the old server Compose and containers unchanged.

- [ ] **Step 4: Verify the green Compose contract and workflow syntax**

Run:

```bash
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example config --format json | jq -e '
  .services.postgres.image == "pgvector/pgvector:0.8.5-pg16@sha256:1d533553fefe4f12e5d80c7b80622ba0c382abb5758856f52983d8789179f0fb"
  and .services.postgres.volumes[0].source == "postgres-pgvector-data"
  and .services.ollama.image == "ollama/ollama:0.30.8@sha256:05b6fe5143ed006d6d4abd39bdd575f962a5822bdf81e6fbb5e6894eb984ab9c"
  and (.services.ollama.ports == null)
  and (.services.ollama.networks | keys == ["default"])
  and ((.services.ollama.mem_limit | tonumber) == 4294967296)
  and (.services.ollama.cpus == 2)
  and (.services.api.depends_on.postgres.condition == "service_healthy")
  and (.services.api.depends_on.ollama.condition == "service_healthy")
'
docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:1.7.7
```

Expected: both Compose commands exit 0; `jq` prints `true`; actionlint exits 0 with no findings.

- [ ] **Step 5: Confirm no secret or host port was introduced**

Run:

```bash
rg -n 'OPENCLAW_API_KEY=|POSTGRES_PASSWORD=|ports:' docker-compose.yml .github/workflows/deploy.yml
```

Expected: no output. The Compose file references secrets through `.env`, and Ollama/PostgreSQL have no published `ports` block.

- [ ] **Step 6: Commit the infrastructure boundary**

```bash
git add docker-compose.yml .github/workflows/deploy.yml
git commit \
  -m "feat(ops): RAG 인프라 전환 경계 추가" \
  -m "pgvector와 내부 Ollama를 고정 이미지로 구성하고 최초 DB 이전 전에는 자동 배포가 서버 구성을 덮어쓰지 않도록 한다." \
  -m $'Constraint: 기존 Alpine PostgreSQL 볼륨은 새 이미지에 연결할 수 없음\nRejected: main 푸시 직후 의존 서비스를 자동 생성 | 빈 새 DB로 API가 기동될 위험\nConfidence: high\nScope-risk: broad\nReversibility: clean\nDirective: rag-v1 마커는 후보 API 검증 완료 후에만 생성할 것\nTested: docker compose config, jq compose assertions, actionlint\nNot-tested: 홈서버 실제 이미지 pull 및 자원 사용량'
```

---

### Task 3: Replace the stale vector schema and add production feedback DDL

**Files:**
- Replace: `backend/docs/db/rag-index-schema.sql`
- Create: `backend/docs/db/coaching-feedback-schema.sql`
- Create: `backend/docs/db/rag-deployment-verify.sql`

**Interfaces:**
- Consumes: existing `member`, `farm`, `crop`, and `farming_record` tables plus `farming-cycle-report-schema.sql`.
- Produces: exact Spring AI 1.1.8 `vector_store` schema and the JPA-compatible coaching persistence tables verified before API startup.

- [ ] **Step 1: Write the schema verifier first**

Create `backend/docs/db/rag-deployment-verify.sql` with exactly:

```sql
\set ON_ERROR_STOP on

do $$
declare
    missing_extensions text;
    missing_tables text;
    actual_embedding_type text;
    missing_columns integer;
begin
    select string_agg(required.name, ', ' order by required.name)
      into missing_extensions
      from (values ('vector'), ('hstore'), ('uuid-ossp')) as required(name)
     where not exists (select 1 from pg_extension extension where extension.extname = required.name);

    if missing_extensions is not null then
        raise exception 'missing required extensions: %', missing_extensions;
    end if;

    select string_agg(required.name, ', ' order by required.name)
      into missing_tables
      from (
          values
              ('public.vector_store'),
              ('public.farming_cycle_report'),
              ('public.record_feedback'),
              ('public.record_feedback_next_action'),
              ('public.report_feedback'),
              ('public.report_feedback_item')
      ) as required(name)
     where to_regclass(required.name) is null;

    if missing_tables is not null then
        raise exception 'missing required tables: %', missing_tables;
    end if;

    select format_type(attribute.atttypid, attribute.atttypmod)
      into actual_embedding_type
      from pg_attribute attribute
     where attribute.attrelid = 'public.vector_store'::regclass
       and attribute.attname = 'embedding'
       and not attribute.attisdropped;

    if actual_embedding_type is distinct from 'vector(1024)' then
        raise exception 'vector_store.embedding must be vector(1024), actual=%', actual_embedding_type;
    end if;

    select count(*)
      into missing_columns
      from (
          values
              ('record_feedback', 'good_point_text', 'character varying'),
              ('record_feedback_next_action', 'text', 'character varying'),
              ('report_feedback', 'source_fingerprint', 'character varying'),
              ('report_feedback', 'summary', 'text'),
              ('report_feedback_item', 'text', 'text')
      ) as required(table_name, column_name, data_type)
     where not exists (
         select 1
           from information_schema.columns column_info
          where column_info.table_schema = 'public'
            and column_info.table_name = required.table_name
            and column_info.column_name = required.column_name
            and column_info.data_type = required.data_type
     );

    if missing_columns <> 0 then
        raise exception 'coaching feedback column contract mismatch count=%', missing_columns;
    end if;

    if not exists (
        select 1
          from information_schema.columns
         where table_schema = 'public'
           and table_name = 'report_feedback'
           and column_name = 'source_fingerprint'
           and character_maximum_length = 64
           and is_nullable = 'YES'
    ) then
        raise exception 'report_feedback.source_fingerprint must be nullable varchar(64)';
    end if;

    if not exists (
        select 1
          from pg_indexes
         where schemaname = 'public'
           and tablename = 'vector_store'
           and indexname = 'spring_ai_vector_index'
           and indexdef ilike '%using hnsw%'
           and indexdef ilike '%vector_cosine_ops%'
    ) then
        raise exception 'spring_ai_vector_index must be HNSW with vector_cosine_ops';
    end if;

    if not exists (
        select 1 from pg_constraint
         where conname = 'uk_record_feedback_record_revision'
           and conrelid = 'public.record_feedback'::regclass
    ) or not exists (
        select 1 from pg_constraint
         where conname = 'uk_report_feedback_report_work_type'
           and conrelid = 'public.report_feedback'::regclass
    ) then
        raise exception 'required coaching uniqueness constraints are missing';
    end if;

    if not exists (
        select 1 from pg_constraint
         where conname = 'record_feedback_status_check'
           and conrelid = 'public.record_feedback'::regclass
           and pg_get_constraintdef(oid) like '%STALE%'
    ) or not exists (
        select 1 from pg_constraint
         where conname = 'report_feedback_status_check'
           and conrelid = 'public.report_feedback'::regclass
           and pg_get_constraintdef(oid) like '%STALE%'
    ) then
        raise exception 'record and report feedback status constraints must allow STALE';
    end if;
end
$$;

select
    format_type(attribute.atttypid, attribute.atttypmod) as embedding_type,
    (select count(*) from public.vector_store) as vector_rows
from pg_attribute attribute
where attribute.attrelid = 'public.vector_store'::regclass
  and attribute.attname = 'embedding'
  and not attribute.attisdropped;
```

- [ ] **Step 2: Prove the old schema fails the new contract**

Run from the repository root against the existing local pgvector container:

```bash
docker exec chamchamcham-postgres dropdb -U chamchamcham --if-exists rag_deployment_contract
docker exec chamchamcham-postgres createdb -U chamchamcham rag_deployment_contract
docker exec -i chamchamcham-postgres psql -U chamchamcham -d rag_deployment_contract <<'SQL'
create table member (id uuid primary key);
create table farm (id uuid primary key);
create table crop (id uuid primary key);
create table farming_record (id uuid primary key);
SQL
docker exec -i chamchamcham-postgres psql -U chamchamcham -d rag_deployment_contract < backend/docs/db/farming-cycle-report-schema.sql
docker exec -i chamchamcham-postgres psql -U chamchamcham -d rag_deployment_contract < backend/docs/db/rag-index-schema.sql
docker exec -i chamchamcham-postgres psql -U chamchamcham -d rag_deployment_contract < backend/docs/db/rag-deployment-verify.sql
```

Expected: the last command fails with `missing required tables` for the four coaching feedback tables. Do not continue if it unexpectedly passes.

- [ ] **Step 3: Replace `rag-index-schema.sql` with the runtime table contract**

Use exactly:

```sql
-- Flyway is not installed. Apply this reviewed schema manually to dev/prod.
\set ON_ERROR_STOP on

begin;

create extension if not exists vector;
create extension if not exists hstore;
create extension if not exists "uuid-ossp";
create schema if not exists public;

create table if not exists public.vector_store (
    id uuid default uuid_generate_v4() primary key,
    content text,
    metadata json,
    embedding vector(1024)
);

drop index if exists public.vector_store_embedding_hnsw_idx;

create index if not exists spring_ai_vector_index
    on public.vector_store using hnsw (embedding vector_cosine_ops);

commit;
```

- [ ] **Step 4: Add `coaching-feedback-schema.sql` matching the current JPA DDL**

Create the file with exactly:

```sql
-- Flyway is not installed. Apply this reviewed schema manually to dev/prod.
\set ON_ERROR_STOP on

begin;

create table if not exists record_feedback (
    created_at timestamp(6) not null,
    source_revision bigint not null,
    updated_at timestamp(6) not null,
    id uuid primary key,
    member_id uuid not null references member (id),
    record_id uuid not null references farming_record (id),
    audit_status varchar(32),
    status varchar(32) not null,
    embedding_model varchar(128),
    failure_code varchar(128),
    model_name varchar(128),
    good_point_basis varchar(255),
    good_point_text varchar(255),
    audit_warnings jsonb not null,
    citations jsonb not null,
    input_snapshot jsonb,
    constraint record_feedback_status_check
        check (status in ('PENDING', 'READY', 'FAILED', 'STALE')),
    constraint uk_record_feedback_record_revision
        unique (record_id, source_revision)
);

create table if not exists record_feedback_next_action (
    display_order integer not null,
    id uuid primary key,
    record_feedback_id uuid not null references record_feedback (id),
    category varchar(32) not null,
    due varchar(32) not null,
    basis varchar(255) not null,
    text varchar(255) not null,
    constraint record_feedback_next_action_category_check
        check (
            category in (
                'WEATHER', 'PEST_DISEASE', 'IRRIGATION', 'FERTILIZING',
                'PEST_CONTROL', 'HARVEST', 'CULTIVATION', 'GENERAL'
            )
        ),
    constraint record_feedback_next_action_due_check
        check (due in ('TODAY', 'THIS_WEEK', 'NEXT_WEEK', 'NEXT_CHECK')),
    constraint uk_record_feedback_next_action_order
        unique (record_feedback_id, display_order)
);

create table if not exists report_feedback (
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    id uuid primary key,
    member_id uuid not null references member (id),
    report_id uuid not null references farming_cycle_report (id),
    audit_status varchar(32),
    status varchar(32) not null,
    work_type varchar(32) not null,
    source_fingerprint varchar(64),
    embedding_model varchar(128),
    failure_code varchar(128),
    model_name varchar(128),
    summary text,
    audit_warnings jsonb not null,
    citations jsonb not null,
    input_snapshot jsonb,
    constraint report_feedback_status_check
        check (status in ('PENDING', 'READY', 'FAILED', 'STALE')),
    constraint report_feedback_work_type_check
        check (
            work_type in (
                'PLANTING', 'WATERING', 'FERTILIZING', 'PEST_CONTROL',
                'WEEDING', 'PRUNING', 'HARVEST', 'ETC'
            )
        ),
    constraint uk_report_feedback_report_work_type
        unique (report_id, work_type)
);

create table if not exists report_feedback_item (
    display_order integer not null,
    id uuid primary key,
    report_feedback_id uuid not null references report_feedback (id),
    section varchar(32) not null,
    basis text not null,
    text text not null,
    constraint report_feedback_item_section_check
        check (section in ('COMPARISON', 'STRENGTH', 'IMPROVEMENT', 'NEXT_ACTION')),
    constraint uk_report_feedback_item_order
        unique (report_feedback_id, display_order)
);

commit;
```

- [ ] **Step 5: Recreate the contract database and prove all SQL is green**

Run:

```bash
docker exec chamchamcham-postgres dropdb -U chamchamcham --if-exists rag_deployment_contract
docker exec chamchamcham-postgres createdb -U chamchamcham rag_deployment_contract
docker exec -i chamchamcham-postgres psql -U chamchamcham -d rag_deployment_contract <<'SQL'
create table member (id uuid primary key);
create table farm (id uuid primary key);
create table crop (id uuid primary key);
create table farming_record (id uuid primary key);
SQL
docker exec -i chamchamcham-postgres psql -U chamchamcham -d rag_deployment_contract < backend/docs/db/farming-cycle-report-schema.sql
docker exec -i chamchamcham-postgres psql -U chamchamcham -d rag_deployment_contract < backend/docs/db/coaching-feedback-schema.sql
docker exec -i chamchamcham-postgres psql -U chamchamcham -d rag_deployment_contract < backend/docs/db/rag-index-schema.sql
docker exec -i chamchamcham-postgres psql -U chamchamcham -d rag_deployment_contract < backend/docs/db/rag-deployment-verify.sql
docker exec chamchamcham-postgres dropdb -U chamchamcham rag_deployment_contract
```

Expected: verifier outputs `embedding_type | vector_rows` as `vector(1024) | 0`; every command exits 0; the disposable database is removed.

- [ ] **Step 6: Confirm the runtime table contract is present**

```bash
rg -n 'public\.vector_store|spring_ai_vector_index' backend/docs/db/rag-index-schema.sql
```

Expected: the Spring AI table and HNSW index definitions are shown.

- [ ] **Step 7: Commit the reviewed SQL contract**

```bash
git add backend/docs/db/rag-index-schema.sql backend/docs/db/coaching-feedback-schema.sql backend/docs/db/rag-deployment-verify.sql
git commit \
  -m "fix(rag): 운영 스키마를 실제 런타임과 통일" \
  -m "Spring AI public.vector_store와 코칭 피드백 테이블을 수동 배포 SQL로 고정한다." \
  -m $'Constraint: Flyway가 없어 운영 DDL을 명시적으로 적용해야 함\nRejected: Hibernate ddl-auto 활성화 | 운영 스키마 변경이 애플리케이션 시작에 숨겨짐\nConfidence: high\nScope-risk: moderate\nDirective: embedding 차원을 바꾸려면 모델, SQL, Spring 설정, 기존 벡터를 함께 마이그레이션할 것\nTested: 임시 pgvector 데이터베이스에 전체 SQL 적용 및 rag-deployment-verify.sql 통과\nNot-tested: 홈서버 기존 데이터 logical restore'
```

---

### Task 4: Remove the stale seed instruction and run repository verification

**Files:**
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/coaching/recordfeedback/RecordFeedbackGenerationVectorStoreSmokeTest.kt:54-57`

**Interfaces:**
- Consumes: Task 3's operational import workflow.
- Produces: accurate smoke-test failure guidance that no longer references an inactive endpoint.

- [ ] **Step 1: Lock the current stale message with a search**

```bash
rg -n 'local dev RAG seed endpoint|Seed real PDF chunks' backend/api/src/test
```

Expected: one result in `RecordFeedbackGenerationVectorStoreSmokeTest.kt`.

- [ ] **Step 2: Replace only the failure message**

Use:

```kotlin
        assertThat(retrieved)
            .withFailMessage("Import verified TECH_DOCUMENT vectors into public.vector_store before running this smoke test.")
            .isNotEmpty
```

- [ ] **Step 3: Run formatting, focused contract tests, and all backend tests**

Run from `backend/`:

```bash
./gradlew :api:test --tests 'com.chamchamcham.config.ProductionRagConfigurationTest'
./gradlew :application:test --tests 'com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackPromptBuilderTest' --tests 'com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackOutputValidatorTest'
./gradlew check
```

Expected: every command reports `BUILD SUCCESSFUL`; local-smoke tests remain skipped unless `RUN_LOCAL_RAG_SMOKE=true` is explicitly set.

- [ ] **Step 4: Commit the smoke guidance**

```bash
git add backend/api/src/test/kotlin/com/chamchamcham/api/coaching/recordfeedback/RecordFeedbackGenerationVectorStoreSmokeTest.kt
git commit \
  -m "test(rag): 벡터 이전 스모크 안내 정정" \
  -m "비활성 시드 엔드포인트 대신 검증된 TECH_DOCUMENT 이전을 선행 조건으로 안내한다." \
  -m $'Confidence: high\nScope-risk: narrow\nTested: focused coaching tests, ./gradlew check\nNot-tested: RUN_LOCAL_RAG_SMOKE 실제 외부 서비스 연동'
```

---

### Task 5: Publish the branch safely and create the release image without auto-migrating

**Files:**
- No new file changes.
- Existing Draft PR: `https://github.com/wingwogus/ChamChamCham/pull/28`

**Interfaces:**
- Consumes: Tasks 1–4 commits; GitHub Actions `deploy.yml`.
- Produces: reviewed commits on `dev`, then a `main` API image tagged `vantagac/chamchamcham-api:latest` while the server stays unchanged because the readiness marker is absent.

- [ ] **Step 1: Verify the branch is clean except the user's `.claude/` directory**

```bash
git status --short --branch
git log --oneline origin/feat/coaching-rag..HEAD
```

Expected: branch `feat/coaching-rag`; only `?? .claude/` is untracked; all planned repository files are committed.

- [ ] **Step 2: Push and update Draft PR #28**

```bash
git push origin feat/coaching-rag
gh pr view 28 --json isDraft,baseRefName,headRefName,url,statusCheckRollup
```

Expected: push succeeds; PR #28 remains Draft from `feat/coaching-rag` to `dev`; checks complete successfully before merge.

- [ ] **Step 3: Use the project release flow**

After PR #28 is reviewed and merged to `dev`, create or update a Draft `dev -> main` release PR using `.github/pull_request_template.md`. Do not merge `main` without the user's explicit release approval because this publishes `latest` and starts the maintenance path.

Verification command before that approval:

```bash
gh pr list --base main --head dev --state open --json number,isDraft,url,title
```

Expected: exactly one Draft release PR is identified or created; its body records the exact Gradle, Compose, SQL, and actionlint checks plus the planned one-time downtime.

- [ ] **Step 4: Confirm the first `main` workflow publishes but skips server deployment**

After the approved release PR is merged, run:

```bash
RUN_ID=$(gh run list --workflow deploy.yml --branch main --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$RUN_ID" --exit-status
gh run view "$RUN_ID" --log | rg 'API image was published, but server deploy was skipped'
```

Expected: build/tests/image push succeed; the log contains the skip notice; no Compose file is copied to the server.

- [ ] **Step 5: Record the published API manifest digest without changing Compose**

```bash
docker buildx imagetools inspect vantagac/chamchamcham-api:latest
```

Expected: output includes a manifest-list digest and a `linux/amd64` manifest. Preserve those two values in the deployment handoff/final report; do not write credentials or environment contents.

---

### Task 6: Verify the source model and export only TECH_DOCUMENT vectors on the MacBook

**Files:**
- Create temporarily outside Git: `/tmp/chamchamcham-rag-transfer/tech-document-vectors.csv`
- Create temporarily outside Git: `/tmp/chamchamcham-rag-transfer/local-embedding.json`
- Create temporarily outside Git: `/tmp/chamchamcham-rag-transfer/TECH_DOCUMENT_SHA256`

**Interfaces:**
- Consumes: local Ollama model already used for the source vectors; `chamchamchamdb.public.vector_store` in container `chamchamcham-postgres`.
- Produces: a mode-600 CSV with 580 rows and a mode-600 reference embedding; both are verified before SCP.

- [ ] **Step 1: Start local Ollama only if necessary and prove `bge-m3` already exists**

```bash
install -d -m 700 /tmp/chamchamcham-rag-transfer
if ! curl -fsS http://127.0.0.1:11434/api/tags >/dev/null; then
  nohup ollama serve >/tmp/chamchamcham-rag-transfer/ollama.log 2>&1 &
  echo $! > /tmp/chamchamcham-rag-transfer/ollama.pid
fi
for attempt in $(seq 1 30); do
  curl -fsS http://127.0.0.1:11434/api/tags >/dev/null && break
  sleep 1
done
LOCAL_MODEL_DIGEST=$(curl -fsS http://127.0.0.1:11434/api/tags | jq -er '.models[] | select(.name == "bge-m3:latest" or .model == "bge-m3:latest") | .digest')
test -n "$LOCAL_MODEL_DIGEST"
printf '%s\n' "$LOCAL_MODEL_DIGEST"
```

Expected: an existing digest is printed. Do not run `ollama pull bge-m3` locally: updating a missing or old model would no longer prove which model produced the stored vectors.

- [ ] **Step 2: Create the fixed reference embedding**

```bash
curl -fsS http://127.0.0.1:11434/api/embed \
  -H 'Content-Type: application/json' \
  -d '{"model":"bge-m3","input":"참당귀 관수 재배 관리 약용작물"}' \
  > /tmp/chamchamcham-rag-transfer/local-embedding.json
chmod 600 /tmp/chamchamcham-rag-transfer/local-embedding.json
jq -e '((.embeddings | length) == 1) and ((.embeddings[0] | length) == 1024)' /tmp/chamchamcham-rag-transfer/local-embedding.json
```

Expected: `jq` prints `true` and exits 0.

- [ ] **Step 3: Revalidate the local source population before export**

```bash
docker exec chamchamcham-postgres psql -U chamchamcham -d chamchamchamdb -v ON_ERROR_STOP=1 -P pager=off -c "
select
    count(*) as tech_rows,
    count(distinct metadata->>'documentTitle') as document_titles,
    count(*) filter (where vector_dims(embedding) <> 1024) as wrong_dimensions,
    count(*) filter (
        where coalesce(metadata->>'sourceType', '') = ''
           or coalesce(metadata->>'cropName', '') = ''
           or coalesce(metadata->>'documentTitle', '') = ''
           or coalesce(metadata->>'page', '') = ''
           or coalesce(metadata->>'pdfPath', '') = ''
    ) as missing_metadata
from public.vector_store
where metadata->>'sourceType' = 'TECH_DOCUMENT';"
```

Expected: `tech_rows=580`, `document_titles=5`, `wrong_dimensions=0`, `missing_metadata=0`.

- [ ] **Step 4: Export the filtered CSV through `COPY TO STDOUT`**

```bash
docker exec chamchamcham-postgres psql -U chamchamcham -d chamchamchamdb -v ON_ERROR_STOP=1 -c "\copy (
  select id, content, metadata, embedding
    from public.vector_store
   where metadata->>'sourceType' = 'TECH_DOCUMENT'
   order by id
) to stdout with (format csv, header true)" \
  > /tmp/chamchamcham-rag-transfer/tech-document-vectors.csv
chmod 600 /tmp/chamchamcham-rag-transfer/tech-document-vectors.csv
(
  cd /tmp/chamchamcham-rag-transfer
  shasum -a 256 tech-document-vectors.csv > TECH_DOCUMENT_SHA256
  shasum -a 256 -c TECH_DOCUMENT_SHA256
)
chmod 600 /tmp/chamchamcham-rag-transfer/TECH_DOCUMENT_SHA256
```

Expected: `tech-document-vectors.csv: OK`. Do not use line count as the CSV record count because PDF content can contain embedded newlines.

---

### Task 7: Back up the home server and stage the new infrastructure without touching the old volume

**Files on server:**
- Preserve: `/home/wingwogus/apps/chamchamcham/docker-compose.yml`
- Preserve with mode 600: `/home/wingwogus/apps/chamchamcham/.env`
- Create: `/home/wingwogus/apps/chamchamcham/backups/$MIGRATION_ID/`
- Stage: `/home/wingwogus/apps/chamchamcham/docker-compose.rag.yml`
- Stage SQL under: `/home/wingwogus/apps/chamchamcham/migration/rag-v1/`

**Interfaces:**
- Consumes: Tasks 2–3 repository files and the current server database.
- Produces: verified custom-format backup, baseline counts, untouched old volume, and staged migration files.

- [ ] **Step 1: Verify `.env` key names and permissions without printing values**

Open a server shell:

```bash
ssh -o BatchMode=yes wingwogus@hyunserver.iptime.org
cd /home/wingwogus/apps/chamchamcham
chmod 600 .env
for key in OLLAMA_BASE_URL OPENCLAW_BASE_URL OPENCLAW_API_KEY OPENCLAW_AGENT_ID OPENCLAW_CONNECT_TIMEOUT_MILLIS OPENCLAW_READ_TIMEOUT_MILLIS RAG_EMBEDDING_MODEL RAG_EMBEDDING_DIMENSION RAG_CHAT_MODEL RAG_VECTOR_TABLE; do
  grep -q "^${key}=" .env || { printf 'missing env key: %s\n' "$key"; exit 1; }
done
stat -c '%a %n' .env
```

Expected: only `600 .env` is printed. This check must not use `cat`, `docker compose config`, `env`, or shell tracing.

- [ ] **Step 2: Create an immutable migration ID and backup directory**

In the same server shell:

```bash
MIGRATION_ID=$(date -u +%Y%m%dT%H%M%SZ)
install -d -m 700 "backups/$MIGRATION_ID" migration/rag-v1
printf '%s\n' "$MIGRATION_ID" > .rag-migration-id
chmod 600 .rag-migration-id
install -m 600 docker-compose.yml "backups/$MIGRATION_ID/docker-compose.yml.pre-rag"
install -m 600 .env "backups/$MIGRATION_ID/env.pre-rag"
OLD_API_IMAGE_ID=$(docker inspect chamchamcham-api --format '{{.Image}}')
docker image tag "$OLD_API_IMAGE_ID" "chamchamcham-api:pre-rag-$MIGRATION_ID"
printf '%s\n' "chamchamcham-api:pre-rag-$MIGRATION_ID" > "backups/$MIGRATION_ID/rollback-api-image.txt"
docker volume inspect chamchamcham_postgres-data >/dev/null
if docker volume inspect chamchamcham_postgres-pgvector-data >/dev/null 2>&1; then
  echo 'new pgvector volume already exists; stop and inspect it instead of reusing or deleting it'
  exit 1
fi
```

Expected: old volume inspection succeeds; the currently running API image is retained under a migration-specific local tag; new volume does not exist; no volume is removed.

- [ ] **Step 3: Capture baseline counts and a custom-format backup**

```bash
MIGRATION_ID=$(<.rag-migration-id)
docker exec chamchamcham-postgres psql -U chamchamcham -d chamchamcham -At -v ON_ERROR_STOP=1 -c "
select 'member|' || count(*) from member
union all select 'farm|' || count(*) from farm
union all select 'crop|' || count(*) from crop
union all select 'farming_record|' || count(*) from farming_record
union all select 'database_bytes|' || pg_database_size(current_database())
order by 1;" > "backups/$MIGRATION_ID/baseline.txt"
docker exec chamchamcham-postgres pg_dump -U chamchamcham -d chamchamcham -Fc -f "/tmp/$MIGRATION_ID.dump"
docker exec chamchamcham-postgres pg_restore --list "/tmp/$MIGRATION_ID.dump" >/dev/null
docker cp "chamchamcham-postgres:/tmp/$MIGRATION_ID.dump" "backups/$MIGRATION_ID/chamchamcham.dump"
chmod 600 "backups/$MIGRATION_ID/chamchamcham.dump" "backups/$MIGRATION_ID/baseline.txt"
sha256sum "backups/$MIGRATION_ID/chamchamcham.dump" > "backups/$MIGRATION_ID/SHA256SUMS"
sha256sum -c "backups/$MIGRATION_ID/SHA256SUMS"
```

Expected: dump checksum reports `OK`; `pg_restore --list` exits 0; no table contents or secrets are printed.

- [ ] **Step 4: Transfer only reviewed repository files and encrypted-by-transport artifacts**

Exit the server shell and run from the repository root on the MacBook:

```bash
scp docker-compose.yml wingwogus@hyunserver.iptime.org:/home/wingwogus/apps/chamchamcham/docker-compose.rag.yml
scp backend/docs/db/farming-cycle-report-schema.sql wingwogus@hyunserver.iptime.org:/home/wingwogus/apps/chamchamcham/migration/rag-v1/
scp backend/docs/db/coaching-feedback-schema.sql wingwogus@hyunserver.iptime.org:/home/wingwogus/apps/chamchamcham/migration/rag-v1/
scp backend/docs/db/rag-index-schema.sql wingwogus@hyunserver.iptime.org:/home/wingwogus/apps/chamchamcham/migration/rag-v1/
scp backend/docs/db/rag-deployment-verify.sql wingwogus@hyunserver.iptime.org:/home/wingwogus/apps/chamchamcham/migration/rag-v1/
scp /tmp/chamchamcham-rag-transfer/tech-document-vectors.csv wingwogus@hyunserver.iptime.org:/home/wingwogus/apps/chamchamcham/migration/rag-v1/
scp /tmp/chamchamcham-rag-transfer/TECH_DOCUMENT_SHA256 wingwogus@hyunserver.iptime.org:/home/wingwogus/apps/chamchamcham/migration/rag-v1/TECH_DOCUMENT_SHA256
```

Expected: all SCP commands succeed. SSH encryption protects transport; file modes are restricted in the next step.

- [ ] **Step 5: Validate staged Compose quietly and record pre-cutover resources**

Back on the server:

```bash
cd /home/wingwogus/apps/chamchamcham
chmod 600 docker-compose.rag.yml migration/rag-v1/tech-document-vectors.csv migration/rag-v1/TECH_DOCUMENT_SHA256
(cd migration/rag-v1 && sha256sum -c TECH_DOCUMENT_SHA256)
docker compose -f docker-compose.rag.yml config --quiet
docker compose -f docker-compose.rag.yml pull postgres ollama api
free -h
df -h /var/lib/docker
docker stats --no-stream
```

Expected: transferred CSV reports `OK`; Compose validation and pulls succeed; disk remains comfortably above the model/image/backup requirement; no secret values are rendered.

---

### Task 8: Restore into the new pgvector volume, verify Ollama compatibility, and import vectors

**Files:**
- Consumes server backup and staged files from Task 7.
- No repository files change.

**Interfaces:**
- Consumes: verified old DB dump, new Compose, local model digest/reference embedding, and filtered CSV.
- Produces: restored PostgreSQL on new volume, verified `bge-m3`, and 580 validated production vector rows.

- [ ] **Step 1: Enter the maintenance window and replace only the Compose definition/container**

On the server:

```bash
cd /home/wingwogus/apps/chamchamcham
MIGRATION_ID=$(<.rag-migration-id)
docker compose stop api postgres
install -m 600 docker-compose.rag.yml docker-compose.yml
docker compose rm -f postgres
docker compose up -d postgres ollama
docker compose ps
```

Expected: API is stopped; `postgres` and `ollama` become healthy. The old container is removed but `chamchamcham_postgres-data` remains untouched.

- [ ] **Step 2: Prove the new PostgreSQL container uses only the new volume**

```bash
docker inspect chamchamcham-postgres --format '{{range .Mounts}}{{println .Name .Destination}}{{end}}'
docker volume inspect chamchamcham_postgres-data >/dev/null
docker volume inspect chamchamcham_postgres-pgvector-data >/dev/null
```

Expected: the PostgreSQL mount line names only `chamchamcham_postgres-pgvector-data /var/lib/postgresql/data`; both old and new volumes exist.

- [ ] **Step 3: Restore the logical backup and compare core row counts**

```bash
MIGRATION_ID=$(<.rag-migration-id)
sha256sum -c "backups/$MIGRATION_ID/SHA256SUMS"
docker cp "backups/$MIGRATION_ID/chamchamcham.dump" "chamchamcham-postgres:/tmp/$MIGRATION_ID.dump"
docker exec chamchamcham-postgres pg_restore --exit-on-error --no-owner --no-privileges -U chamchamcham -d chamchamcham "/tmp/$MIGRATION_ID.dump"
docker exec chamchamcham-postgres psql -U chamchamcham -d chamchamcham -At -v ON_ERROR_STOP=1 -c "
select 'member|' || count(*) from member
union all select 'farm|' || count(*) from farm
union all select 'crop|' || count(*) from crop
union all select 'farming_record|' || count(*) from farming_record
union all select 'database_bytes|' || pg_database_size(current_database())
order by 1;" > "backups/$MIGRATION_ID/restored.txt"
diff <(grep -v '^database_bytes|' "backups/$MIGRATION_ID/baseline.txt") <(grep -v '^database_bytes|' "backups/$MIGRATION_ID/restored.txt")
```

Expected: `pg_restore` exits 0; `diff` prints nothing. Database byte size may differ because the physical image/layout changed, so compare it manually but do not require equality.

- [ ] **Step 4: Apply all reviewed schemas and fail on any mismatch**

```bash
for sql in farming-cycle-report-schema.sql coaching-feedback-schema.sql rag-index-schema.sql rag-deployment-verify.sql; do
  docker exec -i chamchamcham-postgres psql -U chamchamcham -d chamchamcham -v ON_ERROR_STOP=1 < "migration/rag-v1/$sql"
done
```

Expected: every script exits 0; verifier reports `vector(1024)` and initially `0` vector rows.

- [ ] **Step 5: Pull `bge-m3` on the internal Ollama service and compare model digests**

```bash
docker exec chamchamcham-ollama ollama pull bge-m3
docker run --rm --network chamchamcham_default curlimages/curl:8.12.1 -fsS http://ollama:11434/api/tags > migration/rag-v1/server-tags.json
chmod 600 migration/rag-v1/server-tags.json
SERVER_MODEL_DIGEST=$(jq -er '.models[] | select(.name == "bge-m3:latest" or .model == "bge-m3:latest") | .digest' migration/rag-v1/server-tags.json)
printf '%s\n' "$SERVER_MODEL_DIGEST"
```

On the MacBook, compare it to the Task 6 value:

```bash
LOCAL_MODEL_DIGEST=$(curl -fsS http://127.0.0.1:11434/api/tags | jq -er '.models[] | select(.name == "bge-m3:latest" or .model == "bge-m3:latest") | .digest')
SERVER_MODEL_DIGEST=$(ssh wingwogus@hyunserver.iptime.org "jq -er '.models[] | select(.name == \"bge-m3:latest\" or .model == \"bge-m3:latest\") | .digest' /home/wingwogus/apps/chamchamcham/migration/rag-v1/server-tags.json")
test "$LOCAL_MODEL_DIGEST" = "$SERVER_MODEL_DIGEST"
```

Expected: equality test exits 0. If it fails, stop before CSV import.

- [ ] **Step 6: Compare a fixed embedding across MacBook and server**

From the MacBook:

```bash
ssh wingwogus@hyunserver.iptime.org "docker run --rm --network chamchamcham_default curlimages/curl:8.12.1 -fsS http://ollama:11434/api/embed -H 'Content-Type: application/json' -d '{\"model\":\"bge-m3\",\"input\":\"참당귀 관수 재배 관리 약용작물\"}'" > /tmp/chamchamcham-rag-transfer/server-embedding.json
chmod 600 /tmp/chamchamcham-rag-transfer/server-embedding.json
python3 - <<'PY'
import json
import math
from pathlib import Path

base = Path('/tmp/chamchamcham-rag-transfer')
local = json.loads((base / 'local-embedding.json').read_text())['embeddings'][0]
server = json.loads((base / 'server-embedding.json').read_text())['embeddings'][0]
assert len(local) == 1024, len(local)
assert len(server) == 1024, len(server)
dot = sum(left * right for left, right in zip(local, server))
norm = math.sqrt(sum(value * value for value in local)) * math.sqrt(sum(value * value for value in server))
similarity = dot / norm
print(f'cosine_similarity={similarity:.9f}')
assert similarity >= 0.999, similarity
PY
```

Expected: exactly two 1024-dimensional vectors and `cosine_similarity` at least `0.999`.

- [ ] **Step 7: Load CSV into an unlogged staging table and validate before merge**

On the server:

```bash
cd /home/wingwogus/apps/chamchamcham
docker cp migration/rag-v1/tech-document-vectors.csv chamchamcham-postgres:/tmp/tech-document-vectors.csv
docker exec -i chamchamcham-postgres psql -U chamchamcham -d chamchamcham -v ON_ERROR_STOP=1 <<'SQL'
begin;

create unlogged table vector_store_import (
    id uuid primary key,
    content text,
    metadata json,
    embedding vector(1024)
);

\copy vector_store_import (id, content, metadata, embedding) from '/tmp/tech-document-vectors.csv' with (format csv, header true)

do $$
declare
    total_rows integer;
    tech_rows integer;
    farming_rows integer;
    document_titles integer;
    wrong_dimensions integer;
    missing_metadata integer;
begin
    select
        count(*),
        count(*) filter (where metadata->>'sourceType' = 'TECH_DOCUMENT'),
        count(*) filter (where metadata->>'sourceType' = 'FARMING_RECORD'),
        count(distinct metadata->>'documentTitle'),
        count(*) filter (where vector_dims(embedding) <> 1024),
        count(*) filter (
            where coalesce(metadata->>'sourceType', '') = ''
               or coalesce(metadata->>'cropName', '') = ''
               or coalesce(metadata->>'documentTitle', '') = ''
               or coalesce(metadata->>'page', '') = ''
               or coalesce(metadata->>'pdfPath', '') = ''
        )
    into total_rows, tech_rows, farming_rows, document_titles, wrong_dimensions, missing_metadata
    from vector_store_import;

    if total_rows <> 580
       or tech_rows <> 580
       or farming_rows <> 0
       or document_titles <> 5
       or wrong_dimensions <> 0
       or missing_metadata <> 0 then
        raise exception
            'invalid import total=% tech=% farming=% titles=% wrong_dimensions=% missing_metadata=%',
            total_rows, tech_rows, farming_rows, document_titles, wrong_dimensions, missing_metadata;
    end if;
end
$$;

commit;
SQL
```

Expected: transaction commits with no exception. If validation fails, the production `vector_store` remains unchanged.

- [ ] **Step 8: Merge validated vectors and verify the production population**

```bash
docker exec -i chamchamcham-postgres psql -U chamchamcham -d chamchamcham -v ON_ERROR_STOP=1 <<'SQL'
begin;

insert into public.vector_store (id, content, metadata, embedding)
select id, content, metadata, embedding
from vector_store_import
on conflict (id) do update
set content = excluded.content,
    metadata = excluded.metadata,
    embedding = excluded.embedding;

do $$
declare
    total_rows integer;
    tech_rows integer;
    farming_rows integer;
    document_titles integer;
    wrong_dimensions integer;
begin
    select
        count(*),
        count(*) filter (where metadata->>'sourceType' = 'TECH_DOCUMENT'),
        count(*) filter (where metadata->>'sourceType' = 'FARMING_RECORD'),
        count(distinct metadata->>'documentTitle'),
        count(*) filter (where vector_dims(embedding) <> 1024)
    into total_rows, tech_rows, farming_rows, document_titles, wrong_dimensions
    from public.vector_store;

    if total_rows <> 580 or tech_rows <> 580 or farming_rows <> 0 or document_titles <> 5 or wrong_dimensions <> 0 then
        raise exception
            'invalid vector_store total=% tech=% farming=% titles=% wrong_dimensions=%',
            total_rows, tech_rows, farming_rows, document_titles, wrong_dimensions;
    end if;
end
$$;

drop table vector_store_import;
analyze public.vector_store;
commit;
SQL
docker exec -i chamchamcham-postgres psql -U chamchamcham -d chamchamcham -v ON_ERROR_STOP=1 < migration/rag-v1/rag-deployment-verify.sql
```

Expected: production population is exactly 580 TECH documents, 5 titles, zero farming rows; schema verifier reports `vector(1024) | 580`.

---

### Task 9: Start an unrouted candidate API and perform functional RAG/feedback smoke tests

**Files:**
- Temporary server files containing responses under `migration/rag-v1/`, mode 600.
- No repository files change.

**Interfaces:**
- Consumes: `vantagac/chamchamcham-api:latest`, restored PostgreSQL, internal Ollama, internal OpenClaw, and a short-lived valid member JWT supplied interactively.
- Produces: evidence for API startup, document citation, record feedback copy targets, and report feedback length limits before public cutover.

- [ ] **Step 1: Start a candidate container bound only to server loopback**

On the server:

```bash
cd /home/wingwogus/apps/chamchamcham
docker rm -f chamchamcham-api-candidate >/dev/null 2>&1 || true
docker compose run -d --no-deps --name chamchamcham-api-candidate -p 127.0.0.1:18080:8080 api
for attempt in $(seq 1 60); do
  curl -fsS http://127.0.0.1:18080/v3/api-docs >/dev/null && break
  sleep 2
done
curl -fsS http://127.0.0.1:18080/v3/api-docs | jq -e '.openapi and .paths'
docker logs --tail 200 chamchamcham-api-candidate | rg 'Started ApiApplication'
```

Expected: OpenAPI assertion succeeds and startup log is present. Port 18080 is bound to `127.0.0.1`, not `0.0.0.0`.

- [ ] **Step 2: Confirm candidate network reachability and container health**

```bash
docker exec chamchamcham-api-candidate getent hosts postgres ollama openclaw-gateway
docker inspect chamchamcham-api-candidate --format '{{.State.Running}} {{.State.OOMKilled}}'
```

Expected: all three names resolve; state is `true false`.

- [ ] **Step 3: Read a short-lived JWT without echoing or persisting it**

```bash
set +x
read -rsp 'Paste a short-lived ChamChamCham access token: ' ACCESS_TOKEN
printf '\n'
test -n "$ACCESS_TOKEN"
```

Expected: token remains only in the current shell variable. Do not add it to `.env`, a command argument file, shell tracing, or deployment evidence.

- [ ] **Step 4: Prove representative RAG retrieval, OpenClaw generation, and citations end to end**

```bash
curl -fsS http://127.0.0.1:18080/api/v1/coaching/rag/query \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"question":"참당귀 관수 재배 관리 약용작물","mode":"CHAT","topK":6}' \
  > migration/rag-v1/rag-query-response.json
chmod 600 migration/rag-v1/rag-query-response.json
jq -e '
  .data.model.embedding == "bge-m3"
  and .data.model.chat == "openclaw/agri-rag-coach"
  and (.data.result.citations | length > 0)
  and all(.data.result.citations[];
    .sourceType == "TECH_DOCUMENT"
    and (.documentTitle | type == "string" and length > 0)
    and (.page | type == "number")
  )
' migration/rag-v1/rag-query-response.json
```

Expected: `jq` prints `true`. This single request proves API → Ollama embedding → pgvector retrieval → OpenClaw generation, plus citation title/page mapping.

- [ ] **Step 5: Derive the member ID from the JWT and select owned smoke targets**

```bash
JWT_PAYLOAD=$(printf '%s' "$ACCESS_TOKEN" | cut -d. -f2 | tr '_-' '/+')
case $((${#JWT_PAYLOAD} % 4)) in
  2) JWT_PAYLOAD="${JWT_PAYLOAD}==" ;;
  3) JWT_PAYLOAD="${JWT_PAYLOAD}=" ;;
esac
MEMBER_ID=$(printf '%s' "$JWT_PAYLOAD" | base64 -d | jq -er '.sub')
RECORD_ID=$(docker exec chamchamcham-postgres psql -U chamchamcham -d chamchamcham -At -v ON_ERROR_STOP=1 -c "select id from farming_record where member_id = '$MEMBER_ID' and not is_deleted order by worked_at desc, id desc limit 1")
REPORT_ROW=$(docker exec chamchamcham-postgres psql -U chamchamcham -d chamchamcham -At -F '|' -v ON_ERROR_STOP=1 -c "
select report.id, record.work_type
from farming_cycle_report report
join lateral (
    select work_type
    from farming_record record
    where record.member_id = report.member_id
      and record.farm_id = report.farm_id
      and record.crop_id = report.crop_id
      and record.worked_at >= report.starts_at
      and record.worked_at <= report.ends_at
      and not record.is_deleted
    group by work_type
    order by count(*) desc, work_type
    limit 1
) record on true
where report.member_id = '$MEMBER_ID'
  and report.status = 'COMPLETED'
order by report.ends_at desc, report.id desc
limit 1")
REPORT_ID=${REPORT_ROW%%|*}
WORK_TYPE=${REPORT_ROW##*|}
test -n "$RECORD_ID"
test -n "$REPORT_ID"
test -n "$WORK_TYPE"
```

Expected: all three identifiers are non-empty and belong to the authenticated member.

- [ ] **Step 6: Regenerate and poll record feedback, then verify copy targets**

```bash
curl -fsS -X POST "http://127.0.0.1:18080/api/v1/farming-records/$RECORD_ID/feedback/regenerate" \
  -H "Authorization: Bearer $ACCESS_TOKEN" > migration/rag-v1/record-feedback-start.json
for attempt in $(seq 1 60); do
  curl -fsS "http://127.0.0.1:18080/api/v1/farming-records/$RECORD_ID/feedback" \
    -H "Authorization: Bearer $ACCESS_TOKEN" > migration/rag-v1/record-feedback.json
  STATUS=$(jq -r '.data.status' migration/rag-v1/record-feedback.json)
  test "$STATUS" = READY && break
  test "$STATUS" = FAILED && { jq -r '.data.failureCode' migration/rag-v1/record-feedback.json; exit 1; }
  sleep 2
done
chmod 600 migration/rag-v1/record-feedback-start.json migration/rag-v1/record-feedback.json
jq -e '
  .data.status == "READY"
  and ((.data.feedback.goodPoint.text | length) >= 15)
  and ((.data.feedback.goodPoint.text | length) <= 23)
  and ((.data.feedback.nextActions | length) >= 2)
  and ((.data.feedback.nextActions | length) <= 3)
  and all(.data.feedback.nextActions[]; ((.text | length) >= 15) and ((.text | length) <= 25))
' migration/rag-v1/record-feedback.json
```

Expected: status becomes `READY`; `jq` prints `true` for 23/25-character targets.

- [ ] **Step 7: Regenerate and poll report feedback, then verify one paragraph and 65-character limits**

```bash
curl -fsS -X POST "http://127.0.0.1:18080/api/v1/farming-reports/$REPORT_ID/feedback/$WORK_TYPE/regenerate" \
  -H "Authorization: Bearer $ACCESS_TOKEN" > migration/rag-v1/report-feedback-start.json
for attempt in $(seq 1 60); do
  curl -fsS "http://127.0.0.1:18080/api/v1/farming-reports/$REPORT_ID/feedback" \
    -H "Authorization: Bearer $ACCESS_TOKEN" > migration/rag-v1/report-feedback.json
  STATUS=$(jq -r --arg workType "$WORK_TYPE" '.data.feedbacks[] | select(.workType == $workType) | .status' migration/rag-v1/report-feedback.json)
  test "$STATUS" = READY && break
  test "$STATUS" = FAILED && { jq -r --arg workType "$WORK_TYPE" '.data.feedbacks[] | select(.workType == $workType) | .failureCode' migration/rag-v1/report-feedback.json; exit 1; }
  sleep 2
done
chmod 600 migration/rag-v1/report-feedback-start.json migration/rag-v1/report-feedback.json
jq -e --arg workType "$WORK_TYPE" '
  (.data.feedbacks[] | select(.workType == $workType)) as $feedback
  | $feedback.status == "READY"
  and (($feedback.feedback.summary | length) >= 20)
  and (($feedback.feedback.summary | length) <= 65)
  and (($feedback.feedback.summary | contains("\n")) | not)
  and all($feedback.feedback.comparisons[];
    ((.text | length) >= 20) and ((.text | length) <= 65) and ((.text | contains("\n")) | not)
  )
  and all(($feedback.feedback.strengths + $feedback.feedback.improvements + $feedback.feedback.nextActions)[];
    ((.text | length) >= 20) and ((.text | length) <= 65) and ((.text | contains("\n")) | not)
  )
' migration/rag-v1/report-feedback.json
```

Expected: status becomes `READY`; every public section is a single paragraph and within its minimum/65-character limit.

- [ ] **Step 8: Check candidate resources before cutover**

```bash
docker stats --no-stream chamchamcham-api-candidate chamchamcham-postgres chamchamcham-ollama
awk '/MemAvailable/ { print "MemAvailableKiB=" $2; exit !($2 >= 3145728) }' /proc/meminfo
docker inspect chamchamcham-api-candidate chamchamcham-postgres chamchamcham-ollama --format '{{.Name}} OOMKilled={{.State.OOMKilled}} RestartCount={{.RestartCount}}'
```

Expected: at least 3 GiB available; every `OOMKilled=false`; no restart loop.

---

### Task 10: Cut over the API, enable future automatic deploys, and retain rollback evidence

**Files:**
- Server marker: `/home/wingwogus/apps/chamchamcham/.rag-infrastructure-ready`
- Temporary transfer artifacts are removed only after final verification.
- No repository files change.

**Interfaces:**
- Consumes: Task 9's successful candidate evidence.
- Produces: production API container, future safe `main` deploy behavior, and a preserved rollback backup/old volume.

- [ ] **Step 1: Remove the candidate and start the production API service**

```bash
cd /home/wingwogus/apps/chamchamcham
docker rm -f chamchamcham-api-candidate
docker compose up -d api
for attempt in $(seq 1 60); do
  docker exec chamchamcham-api getent hosts postgres ollama openclaw-gateway >/dev/null 2>&1 && break
  sleep 2
done
docker compose ps
docker logs --tail 200 chamchamcham-api | rg 'Started ApiApplication'
```

Expected: production API starts with healthy dependencies and resolves all internal service names.

- [ ] **Step 2: Repeat OpenAPI and authenticated RAG checks against the production service name**

Keep the Task 9 `ACCESS_TOKEN` only in the current server shell. If the shell was closed, read a new short-lived token with the same hidden `read -rsp` command. Run:

```bash
docker run --rm --network chamchamcham_default curlimages/curl:8.12.1 -fsS http://api:8080/v3/api-docs | jq -e '.openapi and .paths'
printf 'header = "Authorization: Bearer %s"\n' "$ACCESS_TOKEN" | \
  docker run --rm -i --network chamchamcham_default curlimages/curl:8.12.1 \
    -fsS -K - \
    -H 'Content-Type: application/json' \
    -d '{"question":"참당귀 관수 재배 관리 약용작물","mode":"CHAT","topK":6}' \
    http://api:8080/api/v1/coaching/rag/query \
    > migration/rag-v1/production-rag-query-response.json
chmod 600 migration/rag-v1/production-rag-query-response.json
jq -e '
  .data.model.embedding == "bge-m3"
  and .data.model.chat == "openclaw/agri-rag-coach"
  and (.data.result.citations | length > 0)
' migration/rag-v1/production-rag-query-response.json
docker logs --since 10m chamchamcham-api | rg -i 'schema-validation|connection refused|unknownhost|outofmemory|exception|error' || true
```

Expected: production-service OpenAPI and representative query succeed; log search has no startup, pgvector, Ollama, or OpenClaw connectivity error. Application-level handled errors must be inspected rather than ignored. Validate the unchanged external Nginx Proxy Manager route once from the existing iOS/dev client before calling the maintenance complete.

- [ ] **Step 3: Create the readiness marker only after all smoke checks pass**

```bash
printf 'rag-v1\n' > .rag-infrastructure-ready
chmod 600 .rag-infrastructure-ready
grep -Fxq 'rag-v1' .rag-infrastructure-ready
```

Expected: exact marker check exits 0. Future `main` workflows may now copy Compose and restart the API.

- [ ] **Step 4: Rerun the deployment workflow and prove the normal path works**

From the repository root on the MacBook:

```bash
gh workflow run deploy.yml --ref main
RUN_ID=$(gh run list --workflow deploy.yml --branch main --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$RUN_ID" --exit-status
```

Expected: build/tests/image push/deploy all succeed; the skip notice is absent; server Compose remains the reviewed RAG version.

- [ ] **Step 5: Observe resources and data for ten minutes before removing transfer files**

On the server, sample `docker stats --no-stream`, `/proc/meminfo`, container restart counts, and recent API logs at least three times over ten minutes, never blocking an agent update for more than 60 seconds. Final acceptance requires:

```bash
awk '/MemAvailable/ { exit !($2 >= 3145728) }' /proc/meminfo
docker inspect chamchamcham-api chamchamcham-postgres chamchamcham-ollama --format '{{.Name}} {{.State.Running}} {{.State.OOMKilled}} {{.RestartCount}}'
docker exec chamchamcham-postgres psql -U chamchamcham -d chamchamcham -At -v ON_ERROR_STOP=1 -c "select count(*), count(distinct metadata->>'documentTitle') from vector_store where metadata->>'sourceType' = 'TECH_DOCUMENT';"
```

Expected: memory threshold passes; each container is running, not OOM-killed, and stable; SQL outputs `580|5`.

- [ ] **Step 6: Remove only temporary transfer copies and keep rollback assets**

After the observation window:

```bash
MIGRATION_ID=$(<.rag-migration-id)
rm migration/rag-v1/tech-document-vectors.csv \
   migration/rag-v1/server-tags.json \
   migration/rag-v1/rag-query-response.json \
   migration/rag-v1/record-feedback-start.json \
   migration/rag-v1/record-feedback.json \
   migration/rag-v1/report-feedback-start.json \
   migration/rag-v1/report-feedback.json \
   migration/rag-v1/production-rag-query-response.json
docker exec chamchamcham-postgres rm -f "/tmp/$MIGRATION_ID.dump" /tmp/tech-document-vectors.csv
```

On the MacBook:

```bash
rm /tmp/chamchamcham-rag-transfer/tech-document-vectors.csv \
   /tmp/chamchamcham-rag-transfer/local-embedding.json \
   /tmp/chamchamcham-rag-transfer/server-embedding.json \
   /tmp/chamchamcham-rag-transfer/TECH_DOCUMENT_SHA256
```

Expected: raw PDF chunk transfer files and embeddings are gone. Keep the server's old PostgreSQL volume, DB dump, checksum, baseline, restored counts, old Compose, and old `.env` backup until a separate retention decision.

- [ ] **Step 7: Record the final evidence in the task handoff**

The final report must include, without secrets:

- Git commit hashes and PR URLs.
- Exact Gradle, Compose, SQL, actionlint, and GitHub Actions results.
- pgvector/Ollama/API image tag and digest evidence.
- Backup checksum verification status, not the dump contents.
- Old and new volume names.
- Restored core row-count comparison result.
- `bge-m3` digest equality and fixed-sentence cosine similarity.
- Vector counts `580`, `5`, `0`, and dimension `1024`.
- Candidate and production RAG/feedback smoke outcomes.
- Minimum available memory observed and OOM/restart status.
- Skipped checks and residual risks, especially N100 CPU concurrency and lack of an in-app PDF reindexer.
- Token usage when the execution surface exposes it; otherwise state that exact token usage was unavailable.

---

## Rollback Procedure

Use this only if any Task 8–10 acceptance check fails. Do not delete the failed new volume during rollback.

On the server:

```bash
cd /home/wingwogus/apps/chamchamcham
MIGRATION_ID=$(<.rag-migration-id)
docker compose stop api postgres ollama
docker compose rm -f api postgres ollama
install -m 600 "backups/$MIGRATION_ID/docker-compose.yml.pre-rag" docker-compose.yml
install -m 600 "backups/$MIGRATION_ID/env.pre-rag" .env
ROLLBACK_API_IMAGE=$(<"backups/$MIGRATION_ID/rollback-api-image.txt")
docker image tag "$ROLLBACK_API_IMAGE" vantagac/chamchamcham-api:latest
rm -f .rag-infrastructure-ready
docker compose up -d postgres
docker inspect chamchamcham-postgres --format '{{range .Mounts}}{{println .Name .Destination}}{{end}}'
docker compose up -d api
docker compose ps
```

Expected:

- PostgreSQL mounts only `chamchamcham_postgres-data`.
- The pgvector-created `chamchamcham_postgres-pgvector-data` remains detached and untouched.
- Old API/PostgreSQL return to their pre-migration state.
- Automatic deployment remains gated because the readiness marker is absent.

Then verify baseline core counts:

```bash
docker exec chamchamcham-postgres psql -U chamchamcham -d chamchamcham -At -v ON_ERROR_STOP=1 -c "
select 'member|' || count(*) from member
union all select 'farm|' || count(*) from farm
union all select 'crop|' || count(*) from crop
union all select 'farming_record|' || count(*) from farming_record
order by 1;" > "backups/$MIGRATION_ID/rollback.txt"
diff <(grep -v '^database_bytes|' "backups/$MIGRATION_ID/baseline.txt") "backups/$MIGRATION_ID/rollback.txt"
```

Expected: `diff` prints nothing.

---

## Final Verification Matrix

| Area | Proof | Required result |
|---|---|---|
| Repository | `./gradlew check` | `BUILD SUCCESSFUL` |
| Compose | `docker compose ... config`, `jq` assertions | digest-pinned pgvector/Ollama, no Ollama host port, correct dependencies |
| Workflow | actionlint + first/second GitHub runs | first skips deploy, second deploys after marker |
| Backup | SHA-256 + `pg_restore --list` | both succeed before downtime |
| Volume isolation | `docker inspect ... .Mounts` | new DB uses only `chamchamcham_postgres-pgvector-data` |
| Restore | baseline/restored count diff | no differences in core tables |
| Schema | `rag-deployment-verify.sql` | extensions/tables/constraints/HNSW/vector(1024) all valid |
| Model | tag digest + fixed embedding | digest equal, dimension 1024, cosine >= 0.999 |
| Data | staged and production SQL assertions | 580 TECH_DOCUMENT, 5 titles, 0 FARMING_RECORD, no missing metadata |
| RAG | authenticated representative query | bge-m3 + OpenClaw response with TECH_DOCUMENT title/page citations |
| Record feedback | regenerate/poll/JQ | READY, good point 15–23, actions 15–25 |
| Report feedback | regenerate/poll/JQ | READY, one paragraph, role minimums, max 65 |
| Capacity | `/proc/meminfo`, inspect/stats | >=3 GiB available, no OOM, no restart loop |
| Rollback | preserved old Compose/env/dump/volume | available until separate retention approval |
