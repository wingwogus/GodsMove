# RAG Pgvector Farming Coaching Design

Date: 2026-07-01

## Purpose

Build the first production-shaped RAG foundation for GodsMove farming coaching.
The MVP exposes an interactive coaching query API, but its application service
boundary must also support later automatic coaching after a farming record is
created and manual coaching from report screens.

The implementation should use the pgvector approach proven in
`/Users/wingwogus/Projects/Viroute/rag-coach-lab`, while adapting it to the
existing Spring Boot Kotlin multi-module backend and existing GodsMove domain
tables.

## Source Context

Current backend:

- Spring Boot Kotlin multi-module service under `backend`.
- Module direction is `api -> application -> domain`; `batch` may call
  `application`.
- Runtime database is PostgreSQL.
- Local profile currently relies on `ddl-auto: create`; dev and prod use
  `ddl-auto: none`.
- Existing domain tables include `farming_record`,
  `farming_record_field_value`, `coaching_feedback`, `crop`, `farm`, and
  `work_type`.
- Flyway is not currently installed.

PoC reference:

- `rag-coach-lab` stores source documents, chunks, embeddings, query runs,
  query sources, and answers.
- It uses `pgvector/pgvector:pg16`, `vector(1024)`, and HNSW cosine index.
- It retrieves chunks with pgvector, calls a chat provider, requires
  `[chunk:<id>]` citations, audits citations, retries once, then falls back to
  extractive snippets when the model still violates citation rules.
- It embeds with local Ollama `bge-m3`.
- It can generate answers through OpenClaw.

## Product Scope

MVP scope:

- Farming coaching RAG only.
- Search both shared technical knowledge and the authenticated member's farming
  records.
- Provide an interactive API first.
- Keep the internal service reusable for later `RECORD_AUTO` and
  `REPORT_MANUAL` coaching modes.
- Add only the minimum pgvector search index table needed for retrieval.
- Do not add query/answer history tables in this pass.
- Do not add Flyway in this pass.
- Use local Ollama for embeddings and OpenClaw for chat generation, including
  local development.

Out of scope for this MVP:

- Automatic coaching trigger after farming record creation.
- Report-screen manual coaching endpoint.
- Query/answer analytics tables.
- Full PDF parsing pipeline inside the backend.
- Flyway/Testcontainers migration hardening.
- New frontend UI.

## Architectural Shape

The API layer stays thin:

- Add `POST /api/v1/coaching/rag/query`.
- Read authenticated `memberId`.
- Validate request shape.
- Map request DTO to an application command.
- Return the application result through the existing `ApiResponse` shape.

The application layer owns RAG orchestration:

- `CoachingRagService` is the public use-case boundary.
- `CoachingRagCommand` carries `mode`, question, optional filters, and top-k.
- `RagIndexRepository` retrieves pgvector chunks.
- `EmbeddingClient` embeds the user question and later indexable content.
- `ChatCompletionClient` calls OpenClaw.
- Prompt construction, citation extraction, citation audit, retry, and
  fallback are deterministic application logic.

The domain layer remains the source of truth:

- `farming_record` and `farming_record_field_value` keep the member's original
  farming data.
- `coaching_feedback` remains the eventual place to persist generated coaching
  when automatic or report-based flows are added.
- The RAG index is derived search data, not the domain source of truth.

Use JDBC-style access for the pgvector index instead of modeling the vector
column as a normal JPA aggregate. The vector type, HNSW index, and nearest
neighbor query are database-search concerns, and forcing them through JPA would
add complexity without improving the domain model.

## RAG Index Table

Add one search index table:

```text
rag_index_chunk
- id
- source_type
- source_id
- member_id
- farm_id
- crop_id
- work_type_id
- record_id
- worked_at
- chunk_index
- content
- content_hash
- embedding vector(1024)
- embedding_model
- metadata
- indexed_at
```

Meaning:

- `source_type`: `TECH_DOCUMENT` or `FARMING_RECORD`.
- `source_id`: stable source identifier. For a farming record, this is the
  record id. For technical content, this is a document/page/section id.
- `member_id`: present for member-owned farming records; null for shared
  technical documents.
- `record_id`: populated when the chunk comes from `farming_record`.
- `worked_at`: populated for farming-record chunks so period filters do not
  need to join the source table during retrieval.
- `content`: search-ready text sent to the LLM as evidence.
- `content_hash`: allows idempotent indexing and later partial reindexing.
- `metadata`: JSON metadata for page number, title, source path, or other
  source-specific attributes. PostgreSQL `jsonb` is preferred in the SQL file;
  the application can still pass serialized JSON through JDBC.

Required database capabilities:

```sql
create extension if not exists vector;
```

The implementation should provide a migration-ready SQL file, for example
`backend/docs/db/rag-index-schema.sql`, containing the extension statement,
table DDL, uniqueness on `(source_type, source_id, chunk_index,
embedding_model)`, normal filter indexes, and an HNSW cosine index over
`embedding`.
This SQL is manually applied for local/dev during MVP work. It can be promoted
to Flyway when deployment schema management is formalized.

## Retrieval Rules

Every interactive query searches:

```text
source_type = TECH_DOCUMENT
or
(source_type = FARMING_RECORD and member_id = currentMemberId)
```

Optional filters narrow the member-owned farming record side:

- `recordId`
- `farmId`
- `cropId`
- `workTypeId`
- `periodStart`
- `periodEnd`
- `topK`

The default top-k is configurable and should start at 6. The service should
enforce a bounded top-k range so clients cannot create expensive retrieval
requests.

Filter examples:

- No filters: shared technical documents plus all indexed records for the
  current member.
- `recordId`: shared technical documents plus one farming record.
- `cropId`: shared technical documents plus the member's records for that crop.
- `farmId`, `cropId`, and period: shared technical documents plus the matching
  subset of member records.

## Indexing Flow

Technical documents:

- MVP should start from text/seed sources rather than building a full PDF
  parser inside the backend.
- The text source is chunked, embedded with Ollama `bge-m3`, and inserted into
  `rag_index_chunk` as `TECH_DOCUMENT`.
- PDF extraction can be added later as a separate batch/admin feature.

Farming records:

- Build indexable text from `farming_record` plus
  `farming_record_field_value`.
- Include crop, work type, worked time, memo, and field values.
- Sensitive or irrelevant parsed fields should be omitted or redacted before
  indexing.
- Store member/farm/crop/work type/record ids in dedicated columns for access
  control and filtering.
- MVP needs a management or batch use case to index existing records.
- Later record creation can call the same `indexFarmingRecord(recordId)` path.

## Interactive API

Endpoint:

```http
POST /api/v1/coaching/rag/query
Authorization: Bearer <access-token>
Content-Type: application/json
```

Request:

```json
{
  "question": "지난주 참당귀 관수 기록 기준으로 과습 위험이 있을까?",
  "farmId": "optional-uuid",
  "cropId": "optional-uuid",
  "workTypeId": "optional-uuid",
  "recordId": "optional-uuid",
  "periodStart": "2026-06-01",
  "periodEnd": "2026-06-30",
  "topK": 6
}
```

Response:

```json
{
  "answer": "근거 기반 코칭 답변",
  "citations": [
    {
      "chunkId": "uuid",
      "sourceType": "FARMING_RECORD",
      "sourceId": "record uuid",
      "label": "영농일지 2026-06-30 관수",
      "similarityScore": 0.82
    }
  ],
  "audit": {
    "status": "PASS",
    "warnings": []
  },
  "model": {
    "embedding": "bge-m3",
    "chat": "openclaw/agri-rag-coach"
  }
}
```

The request filters are optional. `recordId` means "focus on this one record";
it does not change the API into a single-record-only endpoint.

## Prompt And Citation Audit

Prompt requirements:

- Answer in Korean.
- Use only retrieved evidence chunks.
- Attach `[chunk:<id>]` citations to every grounded claim or bullet.
- Never invent chunk ids.
- Say that the current material is insufficient when evidence is not enough.

Audit behavior:

- Extract citations from the answer.
- Fail or warn when the answer is empty, citations are missing, a citation does
  not match a retrieved chunk, or similarity is below the configured threshold.
- Retry once with a stricter citation prompt when citations are missing or
  unknown.
- If the retry still fails for citation compliance, return an extractive
  fallback built from retrieved chunk snippets and mark the audit with a
  warning.
- If no chunks are retrieved, return a normal response stating that the current
  material is insufficient rather than throwing a server error.

## Provider Configuration

MVP provider decisions:

- Embedding provider: local Ollama.
- Embedding model: `bge-m3`.
- Embedding dimension: `1024`.
- Chat provider: OpenClaw.
- Chat model: `openclaw/agri-rag-coach`.
- Local development also uses OpenClaw for chat.

Application ports:

```text
EmbeddingClient
- embed(text): List<Double>

ChatCompletionClient
- complete(messages, model): String
```

Configuration should be environment-driven:

- `OLLAMA_BASE_URL`
- `OPENCLAW_BASE_URL`
- `OPENCLAW_API_KEY`
- `OPENCLAW_AGENT_ID`
- `rag.embedding.model`
- `rag.embedding.dimension`
- `rag.chat.model`
- `rag.top-k-default`
- `rag.timeout-millis`

Do not put provider secrets in committed YAML defaults. Do not expose provider
keys in API responses or logs.

## Persistence Policy

Persist:

- `rag_index_chunk`, because it is the pgvector search index.

Do not persist in this MVP:

- Interactive query logs.
- Interactive generated answers.
- Query-to-source history.

Future persistence:

- Automatic record coaching and report coaching should store generated outputs
  in the existing `coaching_feedback` table.
- `coaching_feedback.source_refs` can store citation metadata as JSON text.
- `summary`, `next_actions`, `input_summary`, and `model_name` can be filled
  from the RAG result when a coaching output needs to become a saved domain
  record.

## Error Handling

Expected client errors:

- Empty question.
- Invalid UUID filters.
- Invalid date range.
- `topK` outside the allowed range.

Expected service errors:

- Ollama embedding provider unavailable.
- OpenClaw unavailable, unauthorized, timed out, or returning invalid JSON.
- Embedding dimension mismatch.
- pgvector schema missing or incompatible.

Use `BusinessException` and stable `ErrorCode` values for expected failures.
Provider secrets, raw prompts containing private farming details, and provider
tokens must not be logged.

Retrieval with no evidence is not a transport failure. Return a successful
response with an insufficient-evidence answer and audit warning.

## Security And Access Control

- Preserve the domain term `member`.
- Never reintroduce project-owned `userId` naming.
- Member-owned chunks must be filtered by authenticated `memberId`.
- Shared technical document chunks must have `member_id` null.
- A member must never be able to retrieve another member's farming record
  chunks through filters.
- Avoid logging full questions when they may contain private farm details.
  Prefer event ids and indirect identifiers in error logs.

## Testing Strategy

Do not add Flyway or Testcontainers in this MVP.

Unit tests:

- Prompt generation.
- Citation extraction.
- Citation audit status and warnings.
- Retry decision logic.
- Extractive fallback generation.
- Question and top-k validation.
- Filter command mapping.

Application tests:

- Use fake or mock `EmbeddingClient`, `ChatCompletionClient`, and
  `RagIndexRepository`.
- Verify normal retrieval/generation.
- Verify no-evidence response.
- Verify citation retry.
- Verify fallback after retry failure.
- Verify optional filters are forwarded to retrieval.

API tests:

- Validate request DTO constraints.
- Verify authenticated member id is mapped to the command.
- Verify successful response shape.
- Verify provider/application business errors map through the global exception
  handler.

SQL tests:

- Test pgvector SQL generation and parameter ordering without requiring H2 to
  execute vector operations.
- Defer real PostgreSQL + pgvector integration verification to a later
  Testcontainers profile or manual pre-deploy check.

## Future Modes

`CHAT`:

- Current MVP.
- User-provided question plus optional filters.
- Returns a response without saving by default.

`RECORD_AUTO`:

- Triggered after a farming record is created.
- Index the record first.
- Generate coaching with `recordId` focus.
- Save into `coaching_feedback`.

`REPORT_MANUAL`:

- Triggered when a report screen asks AI for coaching.
- Include report statistics summary as additional prompt context.
- Retrieve by farm/crop/period filters.
- Save or return according to the report feature decision.

All modes should reuse the same retrieval, prompt, provider, citation audit,
and fallback engine.

## Acceptance Criteria

The design is satisfied when the implementation plan can produce:

- One interactive farming coaching RAG API.
- One minimal pgvector index table SQL file.
- Retrieval over shared technical chunks plus current member farming chunks.
- Optional filters for record, farm, crop, work type, and period.
- Ollama embedding adapter.
- OpenClaw chat adapter.
- Citation audit with retry and fallback.
- No query/answer history tables.
- No Flyway dependency in the MVP.
- Tests covering application behavior without external provider calls.

## Known Follow-Ups

- Add automatic coaching after farming record creation.
- Add report manual coaching.
- Promote schema SQL to Flyway when deployment schema management is ready.
- Add PostgreSQL + pgvector integration tests.
- Add a proper backend PDF/text ingestion pipeline.
- Add RAG quality analytics only after the product needs query history.
