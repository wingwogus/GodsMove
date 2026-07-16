# Medicinal Crop Record RAG Feedback Design

> Status: Superseded
>
> Replacement:
> `2026-07-10-cycle-report-record-coaching-rag-redesign.md`
>
> The replacement removes externally supplied production context, excludes
> cycle statistics from record feedback, and separates record and report output
> contracts.

Date: 2026-07-03

## Purpose

Extend the existing coaching RAG direction into a medicinal-crop specialist
feedback flow for farming records.

The product goal is not a generic chatbot. After a member writes a farming
record, the system should use the current record, crop cycle, farm location,
weather, recent records, work-type statistics, and official medicinal-crop
documents to generate practical feedback for a young return-to-farm farmer.

This design intentionally keeps the RAG boundary independent from the final DB
schema. The record service assembles a stable context payload, and the RAG
feedback engine accepts that payload as its contract.

## Current Decisions

- Use the existing work-type model as the record entry surface.
- The DB team may finalize table shape separately.
- The RAG feedback engine receives a structured context payload assembled by
  an application service, not raw DB access.
- Feedback is generated after a farming record is saved.
- Save response does not need to include the generated feedback body.
- Feedback can be queried separately by record id and status.
- Manual regeneration is not part of the first record-focused pass, but the
  design leaves room for it.
- Photo AI analysis is out of scope for MVP. The context may include
  `hasPhoto` or `photoCount`.
- Post-harvest processing is intentionally light for now.
- The already-downloaded `농업기술길잡이 7 약용작물` PDF is the same file as
  `rda_nongsaro_medicinal_crops_guide_2019.pdf` and must not be indexed twice.

## Existing Source Corpus

Use the tracked source inventory at:

- `/Users/wingwogus/Projects/ChamChamCham/data/rag/medicinal-plants/manifest.csv`
- `/Users/wingwogus/Projects/ChamChamCham/data/rag/medicinal-plants/README.md`

Raw PDFs remain local and ignored by Git under:

- `/Users/wingwogus/Projects/ChamChamCham/data/rag/medicinal-plants/raw/pdfs/`

The initial official corpus includes:

- RDA medicinal-crop farming practical guide I/II, 2005.
- RDA `농업기술길잡이 007 약용작물`, 2019.
- RDA medicinal-crop variety catalog, 2008.
- RDA native medicinal herb cultivation guide, 2011.
- KREI medicinal-crop supply/policy paper, 2012.
- KREI regional medicinal-herb strategy paper, 2008.
- Partial RDA `동의보감 속 식품보감` volumes.
- RDA supporting material for the Korean special resource plants atlas.

Several older PDFs are image-heavy and need OCR before high-quality indexing.
The first implementation should record OCR status and extraction quality in
document metadata so low-quality chunks are not over-trusted.

## Work-Type Assumption

The record context should normalize the current farming record into one of these
work types:

| Code | Label | MVP fields used by RAG |
| --- | --- | --- |
| `PLANTING` | 심기 | sowing amount, transplant count, seed source, memo, photos |
| `WATERING` | 물주기 | water amount scale or liters, watering method, memo, photos |
| `FERTILIZING` | 거름·비료 | material name, amount scale or kg, fertilizing method, memo, photos |
| `PEST_CONTROL` | 병해충 방제 | pesticide name, dose, spray water amount, target pest/disease, memo, photos |
| `WEEDING` | 제초 | weeding method, memo, photos |
| `PRUNING` | 가지·순 정리 | memo, photos |
| `HARVESTING` | 수확 | yield, medicinal use part, cultivated/collected flag, harvest age, memo, photos |
| `PROCESSING` | 가공 | drying form, memo, photos; low priority in MVP |

The RAG layer should not require exact field-table names. It should consume a
generic field map with stable semantic keys.

## Context Contract

Define a stable application-level payload, for example
`TodayRecordFeedbackContext`.

The payload is the contract between the record/domain side and the RAG feedback
engine. Tests can create this payload directly as JSON fixtures without
creating all DB rows first.

```json
{
  "schemaVersion": "record-feedback-context.v1",
  "feedbackRequestId": "feedback-20260703-001",
  "mode": "RECORD_AUTO",
  "member": {
    "memberId": "member-1",
    "experienceLevel": 1,
    "managementType": "NON_REGISTERED_FARMER"
  },
  "farm": {
    "farmId": "farm-1",
    "name": "청년약초밭",
    "address": "강원특별자치도 평창군 진부면",
    "locationSource": "FARM_ADDRESS"
  },
  "crop": {
    "cropId": "crop-angelica",
    "name": "참당귀",
    "usePartCategory": "ROOT_BARK"
  },
  "cropCycle": {
    "cycleId": "cycle-1",
    "startedRecordId": "record-planting-1",
    "startedOn": "2026-04-18",
    "daysAfterPlanting": 76,
    "startBasis": "PLANTING_RECORD"
  },
  "targetRecord": {
    "recordId": "record-20260703-1",
    "recordedOn": "2026-07-03",
    "workType": "WATERING",
    "fields": {
      "waterAmountScale": "보통",
      "wateringMethod": "점적"
    },
    "memo": "오전 흙 표면이 말라 보여 점적 관수함.",
    "hasPhoto": true,
    "photoCount": 1
  },
  "weather": {
    "recordDay": {
      "avgTemperatureC": 24.8,
      "maxTemperatureC": 30.1,
      "minTemperatureC": 19.9,
      "rainfallMm": 0.0,
      "humidityPct": 71.0
    },
    "recent7Days": {
      "rainfallMm": 4.5,
      "hotDaysCount": 2,
      "dryDaysCount": 5
    },
    "source": "SERVER_WEATHER_SNAPSHOT"
  },
  "recentRecords": [
    {
      "recordId": "record-20260629-1",
      "recordedOn": "2026-06-29",
      "workType": "WEEDING",
      "memoSummary": "고랑 주변 손제초"
    }
  ],
  "workTypeStats": {
    "cycleCounts": {
      "PLANTING": 1,
      "WATERING": 8,
      "FERTILIZING": 2,
      "PEST_CONTROL": 1,
      "WEEDING": 3
    },
    "lastWorkedOnByType": {
      "WATERING": "2026-06-30",
      "FERTILIZING": "2026-06-20"
    },
    "recent30DayCounts": {
      "WATERING": 5,
      "WEEDING": 2
    }
  }
}
```

### Required Context Fields

For MVP, feedback quality depends most on:

- `crop.name`
- `crop.usePartCategory`
- `targetRecord.recordedOn`
- `targetRecord.workType`
- `targetRecord.fields`
- `targetRecord.memo`
- `farm.address` or equivalent weather lookup location
- `weather.recordDay`
- `weather.recent7Days`
- `cropCycle.daysAfterPlanting`, when known
- recent record snippets for the same crop cycle
- lightweight work-type statistics

If `cropCycle` is unknown, the system should still generate conservative
record-quality feedback and ask for the missing planting/start information.

## Record Boundary

The first RAG feedback implementation should not modify the farming-record
write model or work-type field storage.

Instead, split the responsibility:

- Record domain/application service owns saving records, weather snapshots,
  crop-cycle inference, and work-type statistics.
- A context assembler later maps those records into
  `TodayRecordFeedbackContext`.
- The RAG feedback engine owns only context validation, retrieval query
  planning, prompt construction, model invocation, output validation, and
  feedback persistence.

This lets the RAG implementation move before the final record schema is
settled, while keeping the integration point explicit. The context payload must
include `schemaVersion` so future record-field changes can be handled by
versioned assemblers instead of breaking prompt or retrieval tests.

## Feedback Flow

```text
Farming record saved
-> Record feedback job created with status PENDING
-> Context assembler loads member/farm/crop/record/weather/recent stats
-> RAG feedback engine receives TodayRecordFeedbackContext
-> Query planner creates retrieval queries from crop + work type + weather risk
-> Official document retriever returns cited chunks
-> LLM generates structured feedback
-> Output validator checks JSON, risk level, confidence, and citations
-> coaching_feedback stores READY or FAILED result
-> Client queries feedback by record id
```

The farming-record save path should not fail just because AI feedback failed.
Provider, OCR, retrieval, or validation failures should update feedback status
to `FAILED` with a retryable reason.

## Retrieval Strategy

Create multiple narrow retrieval queries rather than one broad user-style
question.

Examples:

- `{cropName} {workTypeLabel} 재배 관리 약용작물`
- `{cropName} {daysAfterPlanting}일차 생육 관리`
- `{cropName} 고온 건조 관수 병해충`
- `{cropName} {targetPestOrDisease} 방제`, when pest data exists
- `약용작물 {usePartCategoryLabel} 수확 적기`, for harvest records

Retrieval filters should prefer:

- `sourceType = TECH_DOCUMENT`
- document crop name metadata when available
- work-type or section metadata when available
- high OCR/extraction quality chunks

The RAG engine may also retrieve member-owned `FARMING_RECORD` chunks later,
but MVP feedback should already receive recent record context directly in the
payload. This avoids making the model depend on vector retrieval to understand
the current crop cycle.

## Output Contract

Persist structured feedback, not only prose.

```json
{
  "summary": "오늘 관수 기록은 최근 강수량이 적은 조건을 반영한 조치로 보입니다.",
  "riskLevel": "LOW",
  "confidence": 0.72,
  "recordQuality": {
    "score": "MEDIUM",
    "missingOrWeakFields": ["토양 수분 상태", "관수 지속 시간"],
    "comment": "물주기 판단 근거가 메모에 일부 있지만 다음 기록부터 관수 전후 상태를 더 적는 편이 좋습니다."
  },
  "observations": [
    {
      "title": "최근 건조 조건",
      "detail": "최근 7일 강수량이 적고 고온일이 있어 관수 필요성이 높아질 수 있습니다.",
      "citationIds": []
    }
  ],
  "recommendations": [
    {
      "priority": "MEDIUM",
      "action": "다음 관수 전에는 흙 표면과 뿌리 주변 수분 상태를 함께 기록하세요.",
      "reason": "정량 관수가 어려운 노지 기록에서도 반복 판단 기준을 만들 수 있습니다.",
      "caution": null,
      "citationIds": ["chunk-rda-guide-007-p123"]
    }
  ],
  "nextActions": [
    {
      "due": "NEXT_CHECK",
      "action": "다음 기록에 잎 처짐, 토양 상태, 관수 후 회복 여부를 적기",
      "citationIds": []
    }
  ],
  "followUpQuestions": [
    "최근 밭의 배수 상태가 좋지 않거나 물이 고이는 구역이 있나요?"
  ],
  "citations": [
    {
      "id": "chunk-rda-guide-007-p123",
      "sourceType": "TECH_DOCUMENT",
      "title": "농업기술길잡이 007 약용작물",
      "page": 123
    }
  ],
  "limitations": [
    "사진은 분석하지 않았습니다.",
    "면적과 정확한 관수량이 없어 정밀 처방이 아니라 기록 기반 피드백입니다."
  ]
}
```

### Feedback Tone

The output should be practical and conservative:

- Do not give medical efficacy advice to the grower as a consumer-health claim.
- Do not invent exact fertilizer or pesticide dosage when the registered label
  or area is unavailable.
- Prefer "check", "record", "compare with recent trend", and "verify label"
  wording for uncertain cases.
- Always distinguish document-supported advice from inference based on the
  record/weather context.

## Persistence Model

`coaching_feedback` should be able to represent asynchronous record feedback:

- `id`
- `member_id`
- `mode = RECORD_AUTO`
- `record_id`
- `farm_id`
- `crop_id`
- `work_type`
- `status = PENDING | READY | FAILED`
- `context_hash`
- `prompt_version`
- `document_index_version`
- `summary`
- `risk_level`
- `confidence_score`
- `structured_result jsonb`
- `citations jsonb`
- `failure_code`
- `failure_message`
- `created_at`
- `completed_at`

Use `context_hash + prompt_version + document_index_version` for idempotency
and future regeneration decisions.

Manual regeneration can later create a new feedback row or supersede the old
one, but this is not required for the first pass.

## Testing Strategy

The main RAG feedback tests should use payload fixtures directly.

This keeps tests stable while the DB shape is still owned by another team:

- `today-record-feedback-watering.json`
- `today-record-feedback-fertilizing.json`
- `today-record-feedback-pest-control.json`
- `today-record-feedback-harvest.json`
- `today-record-feedback-no-cycle.json`

These fixture tests are trusted for RAG behavior, not for record persistence.
They prove:

- the payload contract can be parsed and validated;
- work-type-specific retrieval queries are generated correctly;
- prompt context includes crop, weather, target record, recent records, and
  statistics;
- output JSON, citation IDs, confidence, and risk levels are validated;
- insufficient-context cases produce conservative feedback.

They do not prove:

- actual DB records are converted into the payload correctly;
- weather snapshots are attached to the right farm and date;
- crop-cycle inference from planting records is correct;
- work-type statistics are calculated correctly;
- record-save transactions create feedback jobs correctly.

Those guarantees should be covered later by assembler/integration tests after
the farming-record schema stabilizes.

Test layers:

- Context-contract tests deserialize fixture JSON into the application model.
- Query-planner tests assert generated retrieval queries for each work type.
- Prompt tests assert required context sections and safety instructions exist.
- Output-validator tests reject malformed JSON, invalid confidence, and unknown
  citation IDs.
- Persistence-policy tests verify `PENDING`, `READY`, and `FAILED` transitions.
- API tests can mock the context assembler and feedback engine until the DB
  schema stabilizes.

DB-backed tests still matter, but they should focus on the record service's
ability to assemble the payload, not on every RAG behavior.

## MVP Acceptance Criteria

- A saved farming record can create a `PENDING` feedback job.
- The feedback engine can run from a `TodayRecordFeedbackContext` fixture
  without DB setup.
- The context includes target record, crop, farm, weather, crop-cycle estimate,
  recent records, and work-type stats.
- The engine retrieves official medicinal-crop document chunks with citations.
- The output is structured JSON with risk, confidence, recommendations, next
  actions, follow-up questions, citations, and limitations.
- Feedback is persisted and queryable separately from the record save response.
- Failures do not roll back the original farming record.
- Duplicate PDFs are not double-indexed.

## Out Of Scope

- Frontend feedback UI.
- AI photo diagnosis.
- Full post-harvest processing coaching.
- Exact fertilizer prescription per area.
- Pesticide label compliance engine.
- Medical/consumer efficacy recommendations.
- Multi-turn conversational memory.
- Manual feedback regeneration.

## Open Follow-Ups

- Decide whether feedback job creation is handled by an application event,
  message queue, or local transaction hook.
- Decide the final feedback status API shape.
- Decide whether prompt/document index versions are stored as config values or
  table-backed release metadata.
- Add OCR extraction pipeline details after the first indexed text quality
  audit.
