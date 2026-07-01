# Spring AI Coaching RAG Redesign

Date: 2026-07-01

## Purpose

Redesign GodsMove's farming coaching RAG from a hand-rolled pgvector and HTTP
client implementation into a Spring AI-centered architecture.

The goal is not only to reduce custom infrastructure code, but also to make the
coaching feature product-shaped: domain context is explicit, OpenClaw remains
the required chat provider, model output is structured JSON, and durable
coaching results are stored only for modes that represent product records.

## Current Context

The existing MVP introduced a working RAG path:

- `CoachingRagService` orchestrates embedding, retrieval, prompt construction,
  chat completion, citation audit, retry, and fallback.
- `OllamaEmbeddingClient` calls Ollama `/api/embed` directly.
- `RagIndexSqlBuilder` and `RagIndexJdbcRepository` query a custom
  `rag_index_chunk` table with pgvector SQL.
- `OpenClawChatCompletionClient` calls OpenClaw `/v1/chat/completions`
  directly.
- `DevRagSeedService` can seed a local member, farm, crop, farming records,
  technical document chunks, and embeddings.
- `CoachingFeedback` exists, but its current fields fit a simple text summary
  model better than the richer structured output now desired.

This code proved the product path, but the infrastructure shape is too
manual for the next iteration.

## Official Spring AI Alignment

The redesign follows Spring AI's official extension points and RAG building
blocks:

- `ChatClient` as the primary chat entry point.
- `ChatModel` as the provider abstraction for OpenClaw.
- `EmbeddingModel` with Spring AI's Ollama embedding support.
- `VectorStore` with Spring AI's PgVector support.
- `Document` and `metadata` as the unit of indexed knowledge.
- `TokenTextSplitter` and the ETL document pipeline for chunking source text.
- `RetrievalAugmentationAdvisor` with `VectorStoreDocumentRetriever` for RAG.
- Spring AI structured output mapping and schema validation for JSON results.

References:

- https://docs.spring.io/spring-ai/reference/api/chatclient.html
- https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html
- https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html
- https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html
- https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html

## Core Decision

Use Spring AI as the RAG framework boundary and keep GodsMove-specific behavior
in application services.

Replace:

- Custom `EmbeddingClient` implementation with Spring AI `OllamaEmbeddingModel`.
- Custom `rag_index_chunk` retrieval SQL with Spring AI `VectorStore`.
- Custom RAG prompt assembly with `RetrievalAugmentationAdvisor`, while keeping
  domain context and output instructions explicit.
- Custom chat completion port with Spring AI `ChatClient`.

Keep or re-express:

- OpenClaw as the required chat provider via a custom Spring AI `ChatModel`
  adapter.
- Member access control and farm/crop/record filters as Spring AI document
  metadata filters.
- Citation and output quality checks as GodsMove application logic after the
  model response is parsed.
- Mode-specific persistence rules for coaching results.

## Architecture

The high-level request flow is:

```text
API request
-> CoachingCommand
-> CoachingContextProvider
-> VectorStoreDocumentRetriever with metadata filter
-> RetrievalAugmentationAdvisor
-> ChatClient
-> OpenClawChatModel
-> CoachingStructuredResult
-> Citation and schema audit
-> Mode-specific CoachingFeedback persistence
-> API response
```

Main components:

- `CoachingRagService`: use-case boundary. Validates request, builds retrieval
  filters, calls Spring AI, audits output, and applies persistence policy.
- `CoachingContextProvider`: loads member, farm, crop, record, and period
  context. It produces safe prompt context, excluding private contact data and
  credentials.
- `FarmingRecordDocumentFactory`: converts farming records into Spring AI
  `Document` instances.
- `TechnicalDocumentIndexer`: reads or receives technical documents, splits
  them, enriches metadata, and writes them to `VectorStore`.
- `OpenClawChatModel`: implements the Spring AI `ChatModel` contract while
  calling OpenClaw's chat completion API.
- `CoachingStructuredOutputValidator`: validates parsed result ranges,
  citations, and business expectations after Spring AI schema validation.
- `CoachingFeedbackPersistencePolicy`: decides whether to persist the result
  by mode.

## OpenClaw Adapter

OpenClaw is mandatory. It should not remain as a parallel bespoke completion
client outside Spring AI.

Implement a provider adapter:

```text
ChatClient
-> OpenClawChatModel
-> OpenClaw /v1/chat/completions
```

The adapter owns:

- `Authorization: Bearer <api-key>`
- `x-openclaw-agent-id`
- request mapping from Spring AI prompt/messages to OpenClaw payload
- response mapping from OpenClaw choices to Spring AI `ChatResponse`
- provider error mapping to application errors

If OpenClaw proves fully OpenAI-compatible and Spring AI OpenAI configuration
can safely inject the required agent header, this adapter may be reduced later.
The initial design should prefer the explicit adapter because the custom
header is product-critical.

## Vector Store Model

Deprecate the custom `rag_index_chunk` table and use Spring AI's PgVector
`VectorStore`.

Knowledge is represented as Spring AI `Document`:

```text
content:
  natural-language evidence passed to the model

metadata:
  label
  sourceType
  sourceId
  memberId
  farmId
  cropId
  workTypeId
  recordId
  workedAt
  workedAtEpochDay
  documentTitle
  seedName
```

Rules:

- `TECH_DOCUMENT` metadata has no `memberId` and is shared.
- `FARMING_RECORD` metadata must include `memberId`.
- `label` is stored in metadata and is used for citations and UI display.
- Dates should be stored in both ISO string form and numeric epoch-day form if
  range filtering needs stable behavior across Spring AI filter support.
- Do not store email, phone, credentials, tokens, or raw secrets in document
  content or metadata.

The implementation should use the current stable Spring AI BOM compatible with
the backend's Spring Boot version at implementation time.

## Retrieval Rules

Every query retrieves from:

```text
sourceType == TECH_DOCUMENT
or
(sourceType == FARMING_RECORD and memberId == currentMemberId)
```

Optional filters narrow the member-owned side:

- `recordId`
- `farmId`
- `cropId`
- `workTypeId`
- `periodStart`
- `periodEnd`
- `topK`

The retrieval layer should produce a Spring AI filter expression from
`CoachingCommand`. It must never rely on the client to pass `memberId`; the
authenticated principal is the only source of the current member.

## Coaching Context

The model should always receive concise domain context, independent of whether
the retrieved documents happen to contain it.

Prompt context should include only coaching-relevant fields:

- member region
- experience level
- management type when useful
- selected farm name and region
- selected crop name/category/default unit
- record date and work type for record-focused coaching
- report period for report-focused coaching

Prompt context should exclude:

- email
- phone
- password hash
- tokens
- private provider identifiers
- unrelated account data

This prevents the model from depending on accidental retrieval of a farming
record chunk just to know whose farm and crop it is coaching.

## Structured Output

The OpenClaw response should be parsed into a structured application model,
not treated as plain text.

Target result:

```text
CoachingStructuredResult
- summary: String
- riskLevel: LOW | MEDIUM | HIGH | UNKNOWN
- confidence: Double
- observations: List<Observation>
- diagnosis: String
- recommendations: List<Recommendation>
- nextActions: List<NextAction>
- followUpQuestions: List<String>
- citations: List<CitationRef>

Observation
- title: String
- detail: String
- citationIds: List<String>

Recommendation
- priority: HIGH | MEDIUM | LOW
- action: String
- reason: String
- caution: String?
- citationIds: List<String>

NextAction
- due: IMMEDIATE | TODAY | THIS_WEEK | NEXT_CHECK
- action: String
- citationIds: List<String>

CitationRef
- chunkId: String
- label: String
- sourceType: TECH_DOCUMENT | FARMING_RECORD
```

Validation rules:

- `confidence` must be between `0.0` and `1.0`.
- `riskLevel` must be `UNKNOWN` when evidence is insufficient.
- Every recommendation and next action should cite at least one retrieved
  document unless the result is explicitly insufficient-evidence.
- Citation IDs must match retrieved document IDs or stable metadata IDs exposed
  to the model.
- Unknown citation IDs fail audit.
- Schema parsing failure triggers a validation retry.

If OpenClaw cannot reliably produce valid JSON, the service should return a
controlled structured fallback rather than leaking raw provider output.

## API Response

The interactive endpoint should return structured data:

```json
{
  "result": {
    "summary": "...",
    "riskLevel": "MEDIUM",
    "confidence": 0.72,
    "observations": [],
    "diagnosis": "...",
    "recommendations": [],
    "nextActions": [],
    "followUpQuestions": [],
    "citations": []
  },
  "audit": {
    "status": "PASS",
    "warnings": []
  },
  "model": {
    "chat": "openclaw/agri-rag-coach",
    "embedding": "bge-m3"
  },
  "savedFeedbackId": null
}
```

The old `answer: String` response should be removed or treated as a
compatibility field only if an existing client still requires it. The current
frontend is a local test HTML, so the redesign can prefer the structured shape.

## Coaching Feedback Persistence

Redesign `coaching_feedback` around structured JSON, not fixed text-only
output.

Suggested columns:

```text
id
member_id
coaching_mode
record_id nullable
farm_id nullable
crop_id nullable
question text
period_starts_on nullable
period_ends_on nullable
summary text
risk_level varchar(32)
confidence_score numeric
structured_result jsonb
citations jsonb
audit_status varchar(32)
audit_warnings jsonb
model_name varchar(128)
embedding_model varchar(128)
created_at
updated_at
```

Persistence policy:

```text
CHAT
- default: do not save
- purpose: ad hoc interactive coaching and experimentation
- future option: explicit "save this answer" action

RECORD_AUTO
- always save
- record_id is required
- purpose: coaching generated after a farming record is created

REPORT_MANUAL
- always save
- period/farm/crop context is expected
- purpose: report-style coaching history
```

The structured JSON should remain the source of detailed AI output. Columns
such as `summary`, `risk_level`, and `confidence_score` exist for filtering,
lists, and future dashboards.

## Indexing Flow

Technical documents:

```text
PDF/text source
-> DocumentReader or seed reader
-> TokenTextSplitter
-> metadata enrichment
-> VectorStore.add(...)
```

Farming records:

```text
FarmingRecord + Member + Farm + Crop + WorkType + field values
-> FarmingRecordDocumentFactory
-> Document
-> VectorStore.add(...)
```

Farming record document content should be written as concise natural language:

```text
농업인: 박민서
지역: 강원특별자치도 평창군 진부면
농장: 하늘들 약초농장
작물: 참당귀
작업일시: 2026-06-22 07:50
작업유형: 관수
영농일지: ...
```

Indexing must be idempotent. Because Spring AI's default vector store model may
not expose the same uniqueness policy as the custom table, implementation must
define a stable document ID strategy or delete/re-add documents by source
metadata during reindexing.

## Error Handling

Provider and framework failures should map to application errors:

- embedding provider unavailable: `RAG_EMBEDDING_UNAVAILABLE`
- vector store unavailable: `RAG_INDEX_UNAVAILABLE`
- OpenClaw unavailable: `RAG_CHAT_UNAVAILABLE`
- structured output invalid after retry: `RAG_STRUCTURED_OUTPUT_INVALID`
- invalid request/filter/mode: `RAG_INVALID_REQUEST`

Insufficient evidence is not a transport error. It returns HTTP 200 with:

- `riskLevel = UNKNOWN`
- low confidence
- clear explanation
- follow-up questions or missing-data guidance

Citation audit failures should trigger one retry. If the retry still fails,
return a controlled fallback structured result with audit warnings.

## Testing Strategy

Application tests:

- command validation and top-k limits
- context provider excludes sensitive member fields
- retrieval filter construction enforces current member access control
- structured output validation
- citation audit with unknown, missing, and low-similarity citations
- persistence policy for `CHAT`, `RECORD_AUTO`, and `REPORT_MANUAL`

Adapter tests:

- `OpenClawChatModel` sends required auth and agent headers
- OpenClaw response maps to Spring AI chat response
- OpenClaw errors map to application errors
- malformed provider responses are controlled failures

Indexer tests:

- farming record document content includes relevant domain context
- document metadata includes label and filter fields
- idempotent reindexing removes or replaces previous documents for the same
  source

API tests:

- structured response shape
- authenticated member ID is used instead of client-supplied member ID
- invalid date range and invalid top-k are rejected
- mode-specific saved feedback ID behavior

Full provider integration tests with real Ollama, pgvector, and OpenClaw are
outside the first implementation pass. Local manual verification should use
the dev seed/test HTML or curl against the local backend.

## Migration Approach

Because the frontend is not yet integrated and development data can be reset,
the implementation may make a breaking schema change.

Planned migration shape:

1. Introduce Spring AI dependencies and configuration.
2. Add OpenClaw `ChatModel` adapter.
3. Replace custom embedding/retrieval path with Spring AI `VectorStore`.
4. Replace `rag_index_chunk` seed/indexing with Spring AI `Document` indexing.
5. Redesign `coaching_feedback`.
6. Change the coaching API response to structured JSON.
7. Update tests and local dev HTML.
8. Remove or retire obsolete direct RAG classes after behavior is covered.

## Out Of Scope

- Production migration strategy for existing coaching data.
- Full frontend implementation.
- Long-term analytics tables for recommendations and observations.
- Provider-native OpenClaw JSON schema mode unless OpenClaw guarantees support.
- Multi-turn chat memory.
- Cost tracking and prompt telemetry beyond basic model/audit metadata.

## Acceptance Criteria

- RAG generation uses Spring AI `ChatClient` and advisor-based retrieval.
- OpenClaw is invoked through a Spring AI-compatible chat model adapter.
- Embeddings and vector search use Spring AI model/store abstractions.
- Document labels and filter fields are available through metadata.
- Coaching responses are structured JSON.
- `CHAT` results are not saved by default.
- `RECORD_AUTO` and `REPORT_MANUAL` results are saved to redesigned
  `coaching_feedback`.
- Citation audit still protects against unsupported recommendations.
- Tests cover provider adapter mapping, structured output validation, metadata
  filter behavior, and mode-specific persistence.
