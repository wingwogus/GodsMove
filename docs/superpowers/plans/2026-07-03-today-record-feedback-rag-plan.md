# Today Record Feedback RAG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a fixture-driven medicinal-crop record feedback RAG engine that accepts `TodayRecordFeedbackContext` without touching farming-record write storage.

**Architecture:** Add a focused `application.coaching.rag.record` package for the record-feedback payload contract, validation, retrieval query planning, prompt construction, and generation service. Reuse the existing Spring AI `ChatClient`, `VectorStore`, `CoachingStructuredResult`, and `CoachingStructuredOutputValidator`. Do not modify `domain.farming`, farming-record controllers, or work-type field persistence in this plan.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.4, Spring AI 1.1.8, PgVector `VectorStore`, OpenClaw through existing `ChatClient`, JUnit 5, AssertJ, Mockito, Jackson Kotlin module.

---

## Scope Check

This plan implements only the RAG-side contract engine:

- `TodayRecordFeedbackContext` JSON fixture contract.
- Schema-version validation.
- Work-type-aware retrieval query planning.
- Record-context prompt construction.
- Structured record-feedback generation from retrieved official documents.
- Tests that run without farming-record DB setup.

This plan intentionally does not implement:

- `FarmingRecord -> TodayRecordFeedbackContext` assembler.
- Farming-record save hooks.
- Feedback job queue/status API.
- `coaching_feedback` schema changes for `PENDING | READY | FAILED`.
- API endpoint for posting raw feedback context.

Those are separate integration tasks after the farming-record schema stabilizes.

## File Structure

Create:

- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContext.kt`
  - Stable payload model and semantic work-type enum used by the RAG engine.
- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContextValidator.kt`
  - Schema and required-field validation for payload fixtures and future assemblers.
- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackRetrievalQueryPlanner.kt`
  - Builds narrow official-document retrieval queries from crop, work type, crop cycle, weather, and fields.
- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilder.kt`
  - Builds system/user prompt text from the context and retrieved evidence.
- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackService.kt`
  - Orchestrates validation, vector retrieval, prompt building, LLM call, and structured output audit.
- `backend/application/src/test/resources/coaching/rag/today-record-feedback-watering.json`
- `backend/application/src/test/resources/coaching/rag/today-record-feedback-fertilizing.json`
- `backend/application/src/test/resources/coaching/rag/today-record-feedback-pest-control.json`
- `backend/application/src/test/resources/coaching/rag/today-record-feedback-harvest.json`
- `backend/application/src/test/resources/coaching/rag/today-record-feedback-no-cycle.json`
- `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContextTest.kt`
- `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContextValidatorTest.kt`
- `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackRetrievalQueryPlannerTest.kt`
- `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilderTest.kt`
- `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackServiceTest.kt`

Modify:

- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/CoachingStructuredResult.kt`
  - Add `recordQuality` and `limitations` fields with defaults so existing RAG tests keep compiling.
- `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/CoachingStructuredResultTest.kt`
  - Add coverage for the new default fields.
- `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/CoachingStructuredOutputValidatorTest.kt`
  - Add coverage that `UNKNOWN` risk can include uncited limitations without failing audit.

Do not modify:

- `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/**`
- farming-record API/controller files
- work-type field persistence files

## Task 1: Add Payload Fixtures And Context Model

**Files:**

- Create: `backend/application/src/test/resources/coaching/rag/today-record-feedback-watering.json`
- Create: `backend/application/src/test/resources/coaching/rag/today-record-feedback-fertilizing.json`
- Create: `backend/application/src/test/resources/coaching/rag/today-record-feedback-pest-control.json`
- Create: `backend/application/src/test/resources/coaching/rag/today-record-feedback-harvest.json`
- Create: `backend/application/src/test/resources/coaching/rag/today-record-feedback-no-cycle.json`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContextTest.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContext.kt`

- [ ] **Step 1: Add fixture JSON files**

Create `backend/application/src/test/resources/coaching/rag/today-record-feedback-watering.json`:

```json
{
  "schemaVersion": "record-feedback-context.v1",
  "feedbackRequestId": "feedback-20260703-watering",
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
    "recordId": "record-watering-1",
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

Create `backend/application/src/test/resources/coaching/rag/today-record-feedback-fertilizing.json`:

```json
{
  "schemaVersion": "record-feedback-context.v1",
  "feedbackRequestId": "feedback-20260703-fertilizing",
  "mode": "RECORD_AUTO",
  "member": {
    "memberId": "member-1",
    "experienceLevel": 1,
    "managementType": "NON_REGISTERED_FARMER"
  },
  "farm": {
    "farmId": "farm-1",
    "name": "청년약초밭",
    "address": "충청북도 제천시 봉양읍",
    "locationSource": "FARM_ADDRESS"
  },
  "crop": {
    "cropId": "crop-astragalus",
    "name": "황기",
    "usePartCategory": "ROOT_BARK"
  },
  "cropCycle": {
    "cycleId": "cycle-2",
    "startedRecordId": "record-planting-2",
    "startedOn": "2026-04-01",
    "daysAfterPlanting": 93,
    "startBasis": "PLANTING_RECORD"
  },
  "targetRecord": {
    "recordId": "record-fertilizing-1",
    "recordedOn": "2026-07-03",
    "workType": "FERTILIZING",
    "fields": {
      "materialName": "완숙퇴비",
      "amountScale": "보통",
      "fertilizingMethod": "살포"
    },
    "memo": "비 오기 전 고랑 사이에 완숙퇴비를 소량 살포함.",
    "hasPhoto": false,
    "photoCount": 0
  },
  "weather": {
    "recordDay": {
      "avgTemperatureC": 23.4,
      "maxTemperatureC": 28.5,
      "minTemperatureC": 18.7,
      "rainfallMm": 8.0,
      "humidityPct": 82.0
    },
    "recent7Days": {
      "rainfallMm": 23.5,
      "hotDaysCount": 0,
      "dryDaysCount": 1
    },
    "source": "SERVER_WEATHER_SNAPSHOT"
  },
  "recentRecords": [
    {
      "recordId": "record-20260625-1",
      "recordedOn": "2026-06-25",
      "workType": "WATERING",
      "memoSummary": "건조하여 점적 관수"
    }
  ],
  "workTypeStats": {
    "cycleCounts": {
      "PLANTING": 1,
      "WATERING": 6,
      "FERTILIZING": 1
    },
    "lastWorkedOnByType": {
      "FERTILIZING": "2026-05-28"
    },
    "recent30DayCounts": {
      "FERTILIZING": 1,
      "WATERING": 3
    }
  }
}
```

Create `backend/application/src/test/resources/coaching/rag/today-record-feedback-pest-control.json`:

```json
{
  "schemaVersion": "record-feedback-context.v1",
  "feedbackRequestId": "feedback-20260703-pest-control",
  "mode": "RECORD_AUTO",
  "member": {
    "memberId": "member-1",
    "experienceLevel": 1,
    "managementType": "NON_REGISTERED_FARMER"
  },
  "farm": {
    "farmId": "farm-1",
    "name": "청년약초밭",
    "address": "경상북도 영주시 풍기읍",
    "locationSource": "FARM_ADDRESS"
  },
  "crop": {
    "cropId": "crop-ginseng",
    "name": "인삼",
    "usePartCategory": "ROOT_BARK"
  },
  "cropCycle": {
    "cycleId": "cycle-3",
    "startedRecordId": "record-planting-3",
    "startedOn": "2026-03-20",
    "daysAfterPlanting": 105,
    "startBasis": "PLANTING_RECORD"
  },
  "targetRecord": {
    "recordId": "record-pest-control-1",
    "recordedOn": "2026-07-03",
    "workType": "PEST_CONTROL",
    "fields": {
      "pesticideName": "등록약제A",
      "pesticideAmount": "20",
      "pesticideAmountUnit": "mL",
      "sprayWaterAmountL": 20,
      "targetPestOrDisease": "점무늬병"
    },
    "memo": "아랫잎에 반점이 보여 등록약제를 희석해 살포함.",
    "hasPhoto": true,
    "photoCount": 2
  },
  "weather": {
    "recordDay": {
      "avgTemperatureC": 25.1,
      "maxTemperatureC": 29.2,
      "minTemperatureC": 21.6,
      "rainfallMm": 12.0,
      "humidityPct": 88.0
    },
    "recent7Days": {
      "rainfallMm": 41.0,
      "hotDaysCount": 1,
      "dryDaysCount": 0
    },
    "source": "SERVER_WEATHER_SNAPSHOT"
  },
  "recentRecords": [
    {
      "recordId": "record-20260628-1",
      "recordedOn": "2026-06-28",
      "workType": "WEEDING",
      "memoSummary": "포장 가장자리 예초"
    }
  ],
  "workTypeStats": {
    "cycleCounts": {
      "PLANTING": 1,
      "WATERING": 5,
      "PEST_CONTROL": 1,
      "WEEDING": 2
    },
    "lastWorkedOnByType": {
      "PEST_CONTROL": "2026-06-10"
    },
    "recent30DayCounts": {
      "PEST_CONTROL": 1,
      "WEEDING": 2
    }
  }
}
```

Create `backend/application/src/test/resources/coaching/rag/today-record-feedback-harvest.json`:

```json
{
  "schemaVersion": "record-feedback-context.v1",
  "feedbackRequestId": "feedback-20260703-harvest",
  "mode": "RECORD_AUTO",
  "member": {
    "memberId": "member-1",
    "experienceLevel": 1,
    "managementType": "NON_REGISTERED_FARMER"
  },
  "farm": {
    "farmId": "farm-1",
    "name": "청년약초밭",
    "address": "전라남도 구례군",
    "locationSource": "FARM_ADDRESS"
  },
  "crop": {
    "cropId": "crop-omija",
    "name": "오미자",
    "usePartCategory": "FRUIT"
  },
  "cropCycle": {
    "cycleId": "cycle-4",
    "startedRecordId": "record-planting-4",
    "startedOn": "2025-03-15",
    "daysAfterPlanting": 475,
    "startBasis": "PLANTING_RECORD"
  },
  "targetRecord": {
    "recordId": "record-harvest-1",
    "recordedOn": "2026-07-03",
    "workType": "HARVESTING",
    "fields": {
      "yieldAmount": 12,
      "yieldUnit": "kg",
      "medicinalUsePart": "열매",
      "cultivatedOrCollected": "재배",
      "harvestAgeValue": 16,
      "harvestAgeUnit": "월"
    },
    "memo": "붉게 익은 열매 위주로 선별 수확함.",
    "hasPhoto": true,
    "photoCount": 3
  },
  "weather": {
    "recordDay": {
      "avgTemperatureC": 26.0,
      "maxTemperatureC": 31.0,
      "minTemperatureC": 22.0,
      "rainfallMm": 0.0,
      "humidityPct": 68.0
    },
    "recent7Days": {
      "rainfallMm": 6.0,
      "hotDaysCount": 3,
      "dryDaysCount": 4
    },
    "source": "SERVER_WEATHER_SNAPSHOT"
  },
  "recentRecords": [
    {
      "recordId": "record-20260620-1",
      "recordedOn": "2026-06-20",
      "workType": "WATERING",
      "memoSummary": "건조하여 관수"
    }
  ],
  "workTypeStats": {
    "cycleCounts": {
      "PLANTING": 1,
      "WATERING": 12,
      "HARVESTING": 1
    },
    "lastWorkedOnByType": {
      "HARVESTING": "2026-07-03"
    },
    "recent30DayCounts": {
      "WATERING": 4,
      "HARVESTING": 1
    }
  }
}
```

Create `backend/application/src/test/resources/coaching/rag/today-record-feedback-no-cycle.json`:

```json
{
  "schemaVersion": "record-feedback-context.v1",
  "feedbackRequestId": "feedback-20260703-no-cycle",
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
  "cropCycle": null,
  "targetRecord": {
    "recordId": "record-no-cycle-1",
    "recordedOn": "2026-07-03",
    "workType": "WEEDING",
    "fields": {
      "weedingMethod": "손"
    },
    "memo": "작물 주변 잡초를 손으로 제거함.",
    "hasPhoto": false,
    "photoCount": 0
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
  "recentRecords": [],
  "workTypeStats": {
    "cycleCounts": {},
    "lastWorkedOnByType": {},
    "recent30DayCounts": {
      "WEEDING": 1
    }
  }
}
```

- [ ] **Step 2: Write the failing fixture contract test**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContextTest.kt`:

```kotlin
package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.domain.coaching.CoachingMode
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

class TodayRecordFeedbackContextTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()

    @Test
    fun `watering fixture deserializes into stable context contract`() {
        val context = readFixture("today-record-feedback-watering.json")

        assertThat(context.schemaVersion).isEqualTo("record-feedback-context.v1")
        assertThat(context.mode).isEqualTo(CoachingMode.RECORD_AUTO)
        assertThat(context.crop.name).isEqualTo("참당귀")
        assertThat(context.crop.usePartCategory).isEqualTo(CropUsePartCategory.ROOT_BARK)
        assertThat(context.cropCycle?.daysAfterPlanting).isEqualTo(76)
        assertThat(context.targetRecord.workType).isEqualTo(TodayRecordWorkType.WATERING)
        assertThat(context.targetRecord.fieldText("wateringMethod")).isEqualTo("점적")
        assertThat(context.weather.recent7Days.dryDaysCount).isEqualTo(5)
        assertThat(context.workTypeStats.cycleCounts[TodayRecordWorkType.WATERING]).isEqualTo(8)
    }

    @Test
    fun `no cycle fixture keeps crop cycle nullable for conservative feedback`() {
        val context = readFixture("today-record-feedback-no-cycle.json")

        assertThat(context.cropCycle).isNull()
        assertThat(context.targetRecord.workType).isEqualTo(TodayRecordWorkType.WEEDING)
        assertThat(context.recentRecords).isEmpty()
    }

    private fun readFixture(name: String): TodayRecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return objectMapper.readValue(resource)
    }
}
```

- [ ] **Step 3: Run the fixture contract test and verify it fails**

Run from `/Users/wingwogus/Projects/ChamChamCham/backend`:

```bash
./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackContextTest"
```

Expected: compilation fails because `TodayRecordFeedbackContext` and `TodayRecordWorkType` do not exist.

- [ ] **Step 4: Add the context model**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContext.kt`:

```kotlin
package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.domain.coaching.CoachingMode
import com.chamchamcham.domain.crop.CropUsePartCategory
import java.time.LocalDate

const val TODAY_RECORD_FEEDBACK_CONTEXT_SCHEMA_VERSION = "record-feedback-context.v1"

data class TodayRecordFeedbackContext(
    val schemaVersion: String,
    val feedbackRequestId: String,
    val mode: CoachingMode,
    val member: RecordFeedbackMemberContext,
    val farm: RecordFeedbackFarmContext,
    val crop: RecordFeedbackCropContext,
    val cropCycle: RecordFeedbackCropCycleContext?,
    val targetRecord: RecordFeedbackTargetRecordContext,
    val weather: RecordFeedbackWeatherContext,
    val recentRecords: List<RecordFeedbackRecentRecordContext> = emptyList(),
    val workTypeStats: RecordFeedbackWorkTypeStatsContext = RecordFeedbackWorkTypeStatsContext()
)

data class RecordFeedbackMemberContext(
    val memberId: String,
    val experienceLevel: Int?,
    val managementType: String?
)

data class RecordFeedbackFarmContext(
    val farmId: String,
    val name: String,
    val address: String,
    val locationSource: String
)

data class RecordFeedbackCropContext(
    val cropId: String,
    val name: String,
    val usePartCategory: CropUsePartCategory
)

data class RecordFeedbackCropCycleContext(
    val cycleId: String,
    val startedRecordId: String,
    val startedOn: LocalDate,
    val daysAfterPlanting: Int,
    val startBasis: String
)

data class RecordFeedbackTargetRecordContext(
    val recordId: String,
    val recordedOn: LocalDate,
    val workType: TodayRecordWorkType,
    val fields: Map<String, Any?> = emptyMap(),
    val memo: String,
    val hasPhoto: Boolean = false,
    val photoCount: Int = 0
) {
    fun fieldText(key: String): String? {
        return fields[key]?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }
}

data class RecordFeedbackWeatherContext(
    val recordDay: RecordFeedbackRecordDayWeather,
    val recent7Days: RecordFeedbackRecentWeatherSummary,
    val source: String
)

data class RecordFeedbackRecordDayWeather(
    val avgTemperatureC: Double?,
    val maxTemperatureC: Double?,
    val minTemperatureC: Double?,
    val rainfallMm: Double?,
    val humidityPct: Double?
)

data class RecordFeedbackRecentWeatherSummary(
    val rainfallMm: Double?,
    val hotDaysCount: Int?,
    val dryDaysCount: Int?
)

data class RecordFeedbackRecentRecordContext(
    val recordId: String,
    val recordedOn: LocalDate,
    val workType: TodayRecordWorkType,
    val memoSummary: String
)

data class RecordFeedbackWorkTypeStatsContext(
    val cycleCounts: Map<TodayRecordWorkType, Int> = emptyMap(),
    val lastWorkedOnByType: Map<TodayRecordWorkType, LocalDate> = emptyMap(),
    val recent30DayCounts: Map<TodayRecordWorkType, Int> = emptyMap()
)

enum class TodayRecordWorkType(
    val label: String
) {
    PLANTING("심기"),
    WATERING("물주기"),
    FERTILIZING("거름·비료"),
    PEST_CONTROL("병해충 방제"),
    WEEDING("제초"),
    PRUNING("가지·순 정리"),
    HARVESTING("수확"),
    PROCESSING("가공")
}

fun CropUsePartCategory.recordFeedbackLabel(): String {
    return when (this) {
        CropUsePartCategory.WHOLE_HERB -> "전초"
        CropUsePartCategory.ROOT_BARK -> "뿌리·껍질"
        CropUsePartCategory.RHIZOME -> "뿌리줄기"
        CropUsePartCategory.LEAF -> "잎"
        CropUsePartCategory.FLOWER -> "꽃"
        CropUsePartCategory.FRUIT -> "열매/과실"
        CropUsePartCategory.SEED -> "종자"
        CropUsePartCategory.STEM_BRANCH -> "줄기/가지"
        CropUsePartCategory.UNKNOWN -> "기타"
    }
}
```

- [ ] **Step 5: Run the fixture contract test and verify it passes**

Run:

```bash
./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackContextTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit the context contract**

Run from repository root:

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContext.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContextTest.kt backend/application/src/test/resources/coaching/rag/today-record-feedback-watering.json backend/application/src/test/resources/coaching/rag/today-record-feedback-fertilizing.json backend/application/src/test/resources/coaching/rag/today-record-feedback-pest-control.json backend/application/src/test/resources/coaching/rag/today-record-feedback-harvest.json backend/application/src/test/resources/coaching/rag/today-record-feedback-no-cycle.json
git commit -m "feat(rag): 기록 피드백 payload 계약 추가" -m "TodayRecordFeedbackContext를 fixture 기반으로 고정해 기록 저장 모델과 분리된 RAG 테스트가 가능하게 한다." -m "Constraint: farming-record 저장 스키마는 별도 작업에서 확정됨" -m "Confidence: high" -m "Scope-risk: narrow" -m "Tested: ./gradlew :application:test --tests \"com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackContextTest\""
```

## Task 2: Add Context Validation

**Files:**

- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContextValidatorTest.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContextValidator.kt`

- [ ] **Step 1: Write the failing validation tests**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContextValidatorTest.kt`:

```kotlin
package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.domain.coaching.CoachingMode
import com.chamchamcham.domain.crop.CropUsePartCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TodayRecordFeedbackContextValidatorTest {
    private val validator = TodayRecordFeedbackContextValidator()

    @Test
    fun `valid context has no errors`() {
        val result = validator.validate(validContext())

        assertThat(result.errors).isEmpty()
        assertThat(result.warnings).isEmpty()
        assertThat(result.isValid).isTrue()
    }

    @Test
    fun `invalid schema version is an error`() {
        val result = validator.validate(validContext().copy(schemaVersion = "record-feedback-context.v2"))

        assertThat(result.isValid).isFalse()
        assertThat(result.errors).containsExactly("invalid_schema_version")
    }

    @Test
    fun `missing crop cycle is a warning not an error`() {
        val result = validator.validate(validContext().copy(cropCycle = null))

        assertThat(result.isValid).isTrue()
        assertThat(result.warnings).containsExactly("crop_cycle_unknown")
    }

    @Test
    fun `blank target memo is an error because record feedback needs user observation`() {
        val context = validContext().copy(
            targetRecord = validContext().targetRecord.copy(memo = " ")
        )

        val result = validator.validate(context)

        assertThat(result.isValid).isFalse()
        assertThat(result.errors).contains("target_record_memo_blank")
    }

    private fun validContext(): TodayRecordFeedbackContext {
        return TodayRecordFeedbackContext(
            schemaVersion = TODAY_RECORD_FEEDBACK_CONTEXT_SCHEMA_VERSION,
            feedbackRequestId = "feedback-1",
            mode = CoachingMode.RECORD_AUTO,
            member = RecordFeedbackMemberContext("member-1", 1, "NON_REGISTERED_FARMER"),
            farm = RecordFeedbackFarmContext("farm-1", "청년약초밭", "강원특별자치도 평창군", "FARM_ADDRESS"),
            crop = RecordFeedbackCropContext("crop-1", "참당귀", CropUsePartCategory.ROOT_BARK),
            cropCycle = RecordFeedbackCropCycleContext(
                cycleId = "cycle-1",
                startedRecordId = "record-start",
                startedOn = LocalDate.parse("2026-04-18"),
                daysAfterPlanting = 76,
                startBasis = "PLANTING_RECORD"
            ),
            targetRecord = RecordFeedbackTargetRecordContext(
                recordId = "record-1",
                recordedOn = LocalDate.parse("2026-07-03"),
                workType = TodayRecordWorkType.WATERING,
                fields = mapOf("waterAmountScale" to "보통"),
                memo = "흙 표면이 말라 관수함",
                hasPhoto = true,
                photoCount = 1
            ),
            weather = RecordFeedbackWeatherContext(
                recordDay = RecordFeedbackRecordDayWeather(24.8, 30.1, 19.9, 0.0, 71.0),
                recent7Days = RecordFeedbackRecentWeatherSummary(4.5, 2, 5),
                source = "SERVER_WEATHER_SNAPSHOT"
            )
        )
    }
}
```

- [ ] **Step 2: Run validation tests and verify they fail**

Run:

```bash
./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackContextValidatorTest"
```

Expected: compilation fails because `TodayRecordFeedbackContextValidator` does not exist.

- [ ] **Step 3: Add the validator**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContextValidator.kt`:

```kotlin
package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.coaching.CoachingMode
import org.springframework.stereotype.Component

data class RecordFeedbackContextValidationResult(
    val errors: List<String>,
    val warnings: List<String>
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

@Component
class TodayRecordFeedbackContextValidator {
    fun validate(context: TodayRecordFeedbackContext): RecordFeedbackContextValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (context.schemaVersion != TODAY_RECORD_FEEDBACK_CONTEXT_SCHEMA_VERSION) {
            errors += "invalid_schema_version"
        }
        if (context.feedbackRequestId.isBlank()) {
            errors += "feedback_request_id_blank"
        }
        if (context.mode != CoachingMode.RECORD_AUTO) {
            errors += "unsupported_mode:${context.mode.name}"
        }
        if (context.member.memberId.isBlank()) {
            errors += "member_id_blank"
        }
        if (context.farm.farmId.isBlank()) {
            errors += "farm_id_blank"
        }
        if (context.farm.address.isBlank()) {
            errors += "farm_address_blank"
        }
        if (context.crop.cropId.isBlank()) {
            errors += "crop_id_blank"
        }
        if (context.crop.name.isBlank()) {
            errors += "crop_name_blank"
        }
        if (context.targetRecord.recordId.isBlank()) {
            errors += "target_record_id_blank"
        }
        if (context.targetRecord.memo.isBlank()) {
            errors += "target_record_memo_blank"
        }
        if (context.targetRecord.photoCount < 0) {
            errors += "photo_count_negative"
        }
        if (context.cropCycle == null) {
            warnings += "crop_cycle_unknown"
        } else if (context.cropCycle.daysAfterPlanting < 0) {
            errors += "days_after_planting_negative"
        }

        return RecordFeedbackContextValidationResult(
            errors = errors.distinct(),
            warnings = warnings.distinct()
        )
    }

    fun requireValid(context: TodayRecordFeedbackContext): RecordFeedbackContextValidationResult {
        val result = validate(context)
        if (!result.isValid) {
            throw BusinessException(ErrorCode.RAG_INVALID_REQUEST)
        }
        return result
    }
}
```

- [ ] **Step 4: Run validation tests and verify they pass**

Run:

```bash
./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackContextValidatorTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit validation**

Run from repository root:

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContextValidator.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContextValidatorTest.kt
git commit -m "feat(rag): 기록 피드백 컨텍스트 검증 추가" -m "schemaVersion과 필수 payload 필드를 검증해 fixture 기반 RAG 테스트의 계약을 명확히 한다." -m "Constraint: 실제 record assembler는 아직 구현하지 않음" -m "Confidence: high" -m "Scope-risk: narrow" -m "Tested: ./gradlew :application:test --tests \"com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackContextValidatorTest\""
```

## Task 3: Add Work-Type-Aware Retrieval Query Planner

**Files:**

- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackRetrievalQueryPlannerTest.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackRetrievalQueryPlanner.kt`

- [ ] **Step 1: Write failing query-planner tests**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackRetrievalQueryPlannerTest.kt`:

```kotlin
package com.chamchamcham.application.coaching.rag.record

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

class RecordFeedbackRetrievalQueryPlannerTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private val planner = RecordFeedbackRetrievalQueryPlanner()

    @Test
    fun `watering context creates crop work type cycle and dry weather queries`() {
        val queries = planner.plan(readFixture("today-record-feedback-watering.json")).map { it.query }

        assertThat(queries).contains(
            "참당귀 물주기 재배 관리 약용작물",
            "참당귀 76일차 생육 관리",
            "참당귀 고온 건조 관수 병해충"
        )
    }

    @Test
    fun `pest control context includes target disease query`() {
        val queries = planner.plan(readFixture("today-record-feedback-pest-control.json")).map { it.query }

        assertThat(queries).contains(
            "인삼 병해충 방제 재배 관리 약용작물",
            "인삼 점무늬병 방제"
        )
    }

    @Test
    fun `harvest context includes medicinal use part harvest query`() {
        val queries = planner.plan(readFixture("today-record-feedback-harvest.json")).map { it.query }

        assertThat(queries).contains(
            "오미자 수확 재배 관리 약용작물",
            "약용작물 열매/과실 수확 적기 오미자"
        )
    }

    @Test
    fun `no cycle context does not create days after planting query`() {
        val queries = planner.plan(readFixture("today-record-feedback-no-cycle.json")).map { it.query }

        assertThat(queries).contains("참당귀 제초 재배 관리 약용작물")
        assertThat(queries).noneMatch { it.contains("일차 생육 관리") }
    }

    private fun readFixture(name: String): TodayRecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return objectMapper.readValue(resource)
    }
}
```

- [ ] **Step 2: Run query-planner tests and verify they fail**

Run:

```bash
./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.RecordFeedbackRetrievalQueryPlannerTest"
```

Expected: compilation fails because `RecordFeedbackRetrievalQueryPlanner` does not exist.

- [ ] **Step 3: Add query planner**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackRetrievalQueryPlanner.kt`:

```kotlin
package com.chamchamcham.application.coaching.rag.record

import org.springframework.stereotype.Component

data class RecordFeedbackRetrievalQuery(
    val query: String,
    val reason: String
)

@Component
class RecordFeedbackRetrievalQueryPlanner {
    fun plan(context: TodayRecordFeedbackContext): List<RecordFeedbackRetrievalQuery> {
        val cropName = context.crop.name.trim()
        val workTypeLabel = context.targetRecord.workType.label
        val queries = mutableListOf<RecordFeedbackRetrievalQuery>()

        queries += RecordFeedbackRetrievalQuery(
            query = "$cropName $workTypeLabel 재배 관리 약용작물",
            reason = "crop_work_type"
        )

        context.cropCycle?.let { cycle ->
            queries += RecordFeedbackRetrievalQuery(
                query = "$cropName ${cycle.daysAfterPlanting}일차 생육 관리",
                reason = "days_after_planting"
            )
        }

        weatherRiskQuery(context)?.let { queries += it }
        pestControlQuery(context)?.let { queries += it }
        harvestQuery(context)?.let { queries += it }

        return queries
            .distinctBy { it.query }
            .take(4)
    }

    private fun weatherRiskQuery(context: TodayRecordFeedbackContext): RecordFeedbackRetrievalQuery? {
        val cropName = context.crop.name.trim()
        val recent = context.weather.recent7Days
        val recordDay = context.weather.recordDay
        val rainfall = recent.rainfallMm ?: recordDay.rainfallMm
        val dryDays = recent.dryDaysCount ?: 0
        val hotDays = recent.hotDaysCount ?: 0
        val maxTemp = recordDay.maxTemperatureC ?: 0.0

        return when {
            (rainfall != null && rainfall <= 5.0) || dryDays >= 4 || hotDays >= 2 || maxTemp >= 30.0 -> {
                RecordFeedbackRetrievalQuery(
                    query = "$cropName 고온 건조 관수 병해충",
                    reason = "dry_hot_weather"
                )
            }
            rainfall != null && rainfall >= 30.0 -> {
                RecordFeedbackRetrievalQuery(
                    query = "$cropName 강우 과습 배수 병해충",
                    reason = "rain_wet_weather"
                )
            }
            else -> null
        }
    }

    private fun pestControlQuery(context: TodayRecordFeedbackContext): RecordFeedbackRetrievalQuery? {
        if (context.targetRecord.workType != TodayRecordWorkType.PEST_CONTROL) {
            return null
        }
        val target = context.targetRecord.fieldText("targetPestOrDisease") ?: return null
        return RecordFeedbackRetrievalQuery(
            query = "${context.crop.name.trim()} $target 방제",
            reason = "target_pest_or_disease"
        )
    }

    private fun harvestQuery(context: TodayRecordFeedbackContext): RecordFeedbackRetrievalQuery? {
        if (context.targetRecord.workType != TodayRecordWorkType.HARVESTING) {
            return null
        }
        return RecordFeedbackRetrievalQuery(
            query = "약용작물 ${context.crop.usePartCategory.recordFeedbackLabel()} 수확 적기 ${context.crop.name.trim()}",
            reason = "harvest_use_part"
        )
    }
}
```

- [ ] **Step 4: Run query-planner tests and verify they pass**

Run:

```bash
./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.RecordFeedbackRetrievalQueryPlannerTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit query planning**

Run from repository root:

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackRetrievalQueryPlanner.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackRetrievalQueryPlannerTest.kt
git commit -m "feat(rag): 기록 피드백 검색 쿼리 계획 추가" -m "작물, 작업 종류, 생육일수, 날씨 위험, 병해충, 수확 부위를 이용해 공식문서 검색 쿼리를 분리 생성한다." -m "Constraint: 검색은 payload 기반으로만 수행하고 record DB 조회를 하지 않음" -m "Confidence: high" -m "Scope-risk: narrow" -m "Tested: ./gradlew :application:test --tests \"com.chamchamcham.application.coaching.rag.record.RecordFeedbackRetrievalQueryPlannerTest\""
```

## Task 4: Add Record Feedback Prompt Builder

**Files:**

- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilderTest.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilder.kt`

- [ ] **Step 1: Write failing prompt-builder tests**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilderTest.kt`:

```kotlin
package com.chamchamcham.application.coaching.rag.record

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

class RecordFeedbackPromptBuilderTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private val builder = RecordFeedbackPromptBuilder()

    @Test
    fun `prompt includes safety rules for medicinal crop record feedback`() {
        val prompt = builder.build(
            context = readFixture("today-record-feedback-watering.json"),
            queries = listOf(RecordFeedbackRetrievalQuery("참당귀 물주기 재배 관리 약용작물", "crop_work_type")),
            evidence = listOf(RecordFeedbackEvidence("doc-1", "농업기술길잡이 007 약용작물", 123, "관수 후 토양 상태를 확인한다."))
        )

        assertThat(prompt.system).contains("약용작물 영농기록 피드백")
        assertThat(prompt.system).contains("사진은 분석하지 않는다")
        assertThat(prompt.system).contains("의학적 효능")
        assertThat(prompt.system).contains("정확한 비료량이나 농약량을 invent하지 않는다")
    }

    @Test
    fun `prompt includes target record weather stats recent records and evidence`() {
        val prompt = builder.build(
            context = readFixture("today-record-feedback-watering.json"),
            queries = listOf(RecordFeedbackRetrievalQuery("참당귀 물주기 재배 관리 약용작물", "crop_work_type")),
            evidence = listOf(RecordFeedbackEvidence("doc-1", "농업기술길잡이 007 약용작물", 123, "관수 후 토양 상태를 확인한다."))
        )

        assertThat(prompt.user).contains("작물: 참당귀")
        assertThat(prompt.user).contains("작업유형: 물주기")
        assertThat(prompt.user).contains("오전 흙 표면이 말라 보여 점적 관수함.")
        assertThat(prompt.user).contains("최근 7일 강수량: 4.5mm")
        assertThat(prompt.user).contains("WATERING=8")
        assertThat(prompt.user).contains("[doc-1] 농업기술길잡이 007 약용작물 p.123")
    }

    private fun readFixture(name: String): TodayRecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return objectMapper.readValue(resource)
    }
}
```

- [ ] **Step 2: Run prompt-builder tests and verify they fail**

Run:

```bash
./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.RecordFeedbackPromptBuilderTest"
```

Expected: compilation fails because `RecordFeedbackPromptBuilder` and `RecordFeedbackEvidence` do not exist.

- [ ] **Step 3: Add prompt builder**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilder.kt`:

```kotlin
package com.chamchamcham.application.coaching.rag.record

import org.springframework.stereotype.Component

data class RecordFeedbackEvidence(
    val id: String,
    val title: String,
    val page: Int?,
    val content: String
)

data class RecordFeedbackPrompt(
    val system: String,
    val user: String
)

@Component
class RecordFeedbackPromptBuilder {
    fun build(
        context: TodayRecordFeedbackContext,
        queries: List<RecordFeedbackRetrievalQuery>,
        evidence: List<RecordFeedbackEvidence>
    ): RecordFeedbackPrompt {
        return RecordFeedbackPrompt(
            system = systemPrompt(),
            user = userPrompt(context, queries, evidence)
        )
    }

    private fun systemPrompt(): String {
        return """
            너는 약용작물 영농기록 피드백 보조자다.
            입력된 TodayRecordFeedbackContext와 검색된 공식문서 근거만 사용한다.
            사진은 분석하지 않는다. hasPhoto와 photoCount는 사진 첨부 여부로만 해석한다.
            의학적 효능, 복용법, 질병 치료 효과를 소비자 건강 조언처럼 말하지 않는다.
            면적, 등록 라벨, 희석 기준이 없으면 정확한 비료량이나 농약량을 invent하지 않는다.
            근거가 부족하면 riskLevel은 UNKNOWN, confidence는 0.3 이하로 둔다.
            권장사항은 확인, 기록, 비교, 라벨 확인처럼 보수적인 행동으로 작성한다.
            document-supported advice와 record/weather inference를 구분해서 설명한다.
            응답은 CoachingStructuredResult JSON schema만 따른다.
        """.trimIndent()
    }

    private fun userPrompt(
        context: TodayRecordFeedbackContext,
        queries: List<RecordFeedbackRetrievalQuery>,
        evidence: List<RecordFeedbackEvidence>
    ): String {
        return """
            피드백 대상 기록:
            ${formatContext(context)}

            검색 쿼리:
            ${queries.joinToString("\n") { "- ${it.query} (${it.reason})" }}

            공식문서 근거:
            ${formatEvidence(evidence)}
        """.trimIndent()
    }

    private fun formatContext(context: TodayRecordFeedbackContext): String {
        val cycle = context.cropCycle
        val target = context.targetRecord
        val recordDay = context.weather.recordDay
        val recentWeather = context.weather.recent7Days

        return buildString {
            appendLine("- schemaVersion: ${context.schemaVersion}")
            appendLine("- feedbackRequestId: ${context.feedbackRequestId}")
            appendLine("- 농장: ${context.farm.name} (${context.farm.address})")
            appendLine("- 작물: ${context.crop.name} / 약용부위분류: ${context.crop.usePartCategory.recordFeedbackLabel()}")
            appendLine("- 작물주기: ${cycle?.daysAfterPlanting?.let { "${it}일차" } ?: "미상"}")
            appendLine("- 기록일: ${target.recordedOn}")
            appendLine("- 작업유형: ${target.workType.label}")
            appendLine("- 메모: ${target.memo}")
            appendLine("- 사진첨부: ${target.hasPhoto}, 사진수: ${target.photoCount}")
            appendLine("- 필드: ${formatMap(target.fields)}")
            appendLine("- 당일 날씨: 평균 ${recordDay.avgTemperatureC ?: "미상"}C, 최고 ${recordDay.maxTemperatureC ?: "미상"}C, 강수 ${recordDay.rainfallMm ?: "미상"}mm, 습도 ${recordDay.humidityPct ?: "미상"}%")
            appendLine("- 최근 7일 강수량: ${recentWeather.rainfallMm ?: "미상"}mm, 고온일수: ${recentWeather.hotDaysCount ?: "미상"}, 건조일수: ${recentWeather.dryDaysCount ?: "미상"}")
            appendLine("- 최근 기록: ${formatRecentRecords(context.recentRecords)}")
            appendLine("- 주기별 작업 횟수: ${formatMap(context.workTypeStats.cycleCounts)}")
            appendLine("- 최근 30일 작업 횟수: ${formatMap(context.workTypeStats.recent30DayCounts)}")
        }.trim()
    }

    private fun formatRecentRecords(records: List<RecordFeedbackRecentRecordContext>): String {
        if (records.isEmpty()) {
            return "없음"
        }
        return records.joinToString(" | ") {
            "${it.recordedOn} ${it.workType.label}: ${it.memoSummary}"
        }
    }

    private fun formatEvidence(evidence: List<RecordFeedbackEvidence>): String {
        if (evidence.isEmpty()) {
            return "검색된 공식문서 근거 없음"
        }
        return evidence.joinToString("\n\n") {
            val page = it.page?.let { page -> " p.$page" } ?: ""
            "[${it.id}] ${it.title}$page\n${it.content}"
        }
    }

    private fun formatMap(map: Map<*, *>): String {
        if (map.isEmpty()) {
            return "없음"
        }
        return map.entries.joinToString(", ") { "${it.key}=${it.value}" }
    }
}
```

- [ ] **Step 4: Run prompt-builder tests and verify they pass**

Run:

```bash
./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.RecordFeedbackPromptBuilderTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit prompt builder**

Run from repository root:

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilder.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilderTest.kt
git commit -m "feat(rag): 기록 피드백 프롬프트 생성 추가" -m "TodayRecordFeedbackContext와 공식문서 근거를 구조화된 약용작물 기록 피드백 프롬프트로 변환한다." -m "Constraint: 사진 분석, 의학 효능 조언, 정밀 비료·농약 처방은 MVP 범위에서 제외" -m "Confidence: high" -m "Scope-risk: narrow" -m "Tested: ./gradlew :application:test --tests \"com.chamchamcham.application.coaching.rag.record.RecordFeedbackPromptBuilderTest\""
```

## Task 5: Extend Structured Output For Record Feedback

**Files:**

- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/CoachingStructuredResult.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/CoachingStructuredResultTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/CoachingStructuredOutputValidatorTest.kt`

- [ ] **Step 1: Add failing tests for record feedback output fields**

Append to `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/CoachingStructuredResultTest.kt`:

```kotlin
    @Test
    fun `structured result exposes default record quality and limitations for record feedback`() {
        val result = CoachingStructuredResult.insufficientEvidence("근거 부족")

        assertThat(result.recordQuality.score).isEqualTo(CoachingRecordQualityScore.UNKNOWN)
        assertThat(result.recordQuality.missingOrWeakFields).isEmpty()
        assertThat(result.limitations).contains("근거가 부족해 보수적으로 판단했습니다.")
    }
```

Append to `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/CoachingStructuredOutputValidatorTest.kt`:

```kotlin
    @Test
    fun `unknown risk with limitations and no citations can pass audit`() {
        val result = CoachingStructuredResult.insufficientEvidence("근거 부족")

        val audit = validator.validate(result, emptySet())

        assertThat(audit.status).isEqualTo(RagAuditStatus.PASS)
    }
```

- [ ] **Step 2: Run structured result tests and verify they fail**

Run:

```bash
./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.CoachingStructuredResultTest" --tests "com.chamchamcham.application.coaching.rag.CoachingStructuredOutputValidatorTest"
```

Expected: compilation fails because `recordQuality`, `limitations`, and `CoachingRecordQualityScore` do not exist.

- [ ] **Step 3: Modify `CoachingStructuredResult.kt`**

Update `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/CoachingStructuredResult.kt` so the full file contains:

```kotlin
package com.chamchamcham.application.coaching.rag

enum class CoachingRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN
}

enum class CoachingPriority {
    HIGH,
    MEDIUM,
    LOW
}

enum class CoachingActionDue {
    IMMEDIATE,
    TODAY,
    THIS_WEEK,
    NEXT_CHECK
}

enum class CoachingRecordQualityScore {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}

data class CoachingStructuredResult(
    val summary: String,
    val riskLevel: CoachingRiskLevel,
    val confidence: Double,
    val observations: List<CoachingObservation>,
    val diagnosis: String,
    val recommendations: List<CoachingRecommendation>,
    val nextActions: List<CoachingNextAction>,
    val followUpQuestions: List<String>,
    val citations: List<CoachingCitationRef>,
    val recordQuality: CoachingRecordQuality = CoachingRecordQuality(),
    val limitations: List<String> = emptyList()
) {
    companion object {
        fun insufficientEvidence(message: String): CoachingStructuredResult {
            return CoachingStructuredResult(
                summary = message,
                riskLevel = CoachingRiskLevel.UNKNOWN,
                confidence = 0.0,
                observations = emptyList(),
                diagnosis = message,
                recommendations = emptyList(),
                nextActions = emptyList(),
                followUpQuestions = listOf("최근 영농기록이나 작물 상태 정보를 추가로 입력해주세요."),
                citations = emptyList(),
                recordQuality = CoachingRecordQuality(
                    score = CoachingRecordQualityScore.UNKNOWN,
                    missingOrWeakFields = emptyList(),
                    comment = "현재 근거만으로 기록 품질을 평가하기 어렵습니다."
                ),
                limitations = listOf("근거가 부족해 보수적으로 판단했습니다.")
            )
        }
    }
}

data class CoachingRecordQuality(
    val score: CoachingRecordQualityScore = CoachingRecordQualityScore.UNKNOWN,
    val missingOrWeakFields: List<String> = emptyList(),
    val comment: String = ""
)

data class CoachingObservation(
    val title: String,
    val detail: String,
    val citationIds: List<String>
)

data class CoachingRecommendation(
    val priority: CoachingPriority,
    val action: String,
    val reason: String,
    val caution: String? = null,
    val citationIds: List<String>
)

data class CoachingNextAction(
    val due: CoachingActionDue,
    val action: String,
    val citationIds: List<String>
)

data class CoachingCitationRef(
    val chunkId: String,
    val label: String,
    val sourceType: RagSourceType
)
```

- [ ] **Step 4: Run structured result tests and verify they pass**

Run:

```bash
./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.CoachingStructuredResultTest" --tests "com.chamchamcham.application.coaching.rag.CoachingStructuredOutputValidatorTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit structured output extension**

Run from repository root:

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/CoachingStructuredResult.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/CoachingStructuredResultTest.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/CoachingStructuredOutputValidatorTest.kt
git commit -m "feat(rag): 기록 품질 필드를 구조화 응답에 추가" -m "기록 기반 피드백에서 누락 필드와 한계를 명시할 수 있도록 recordQuality와 limitations를 추가한다." -m "Constraint: 기존 RAG 테스트가 깨지지 않도록 새 필드는 기본값을 가진다" -m "Confidence: medium" -m "Scope-risk: moderate" -m "Tested: ./gradlew :application:test --tests \"com.chamchamcham.application.coaching.rag.CoachingStructuredResultTest\" --tests \"com.chamchamcham.application.coaching.rag.CoachingStructuredOutputValidatorTest\""
```

## Task 6: Add Today Record Feedback Service

**Files:**

- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackServiceTest.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackService.kt`

- [ ] **Step 1: Write failing service tests**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackServiceTest.kt`:

```kotlin
package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.application.coaching.rag.CoachingActionDue
import com.chamchamcham.application.coaching.rag.CoachingCitationRef
import com.chamchamcham.application.coaching.rag.CoachingNextAction
import com.chamchamcham.application.coaching.rag.CoachingObservation
import com.chamchamcham.application.coaching.rag.CoachingPriority
import com.chamchamcham.application.coaching.rag.CoachingRecommendation
import com.chamchamcham.application.coaching.rag.CoachingRiskLevel
import com.chamchamcham.application.coaching.rag.CoachingStructuredOutputValidator
import com.chamchamcham.application.coaching.rag.CoachingStructuredResult
import com.chamchamcham.application.coaching.rag.RagProperties
import com.chamchamcham.application.coaching.rag.RagSourceType
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.ResponseEntity
import org.springframework.ai.chat.client.advisor.api.Advisor
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.converter.StructuredOutputConverter
import org.springframework.ai.document.Document
import org.springframework.ai.template.TemplateRenderer
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.Filter
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.Resource
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.nio.charset.Charset
import java.util.function.Consumer

class TodayRecordFeedbackServiceTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()

    @Test
    fun `generate rejects invalid schema before vector search`() {
        val vectorStore = FakeVectorStore(emptyList())
        val service = service(vectorStore = vectorStore)
        val context = readFixture("today-record-feedback-watering.json").copy(schemaVersion = "record-feedback-context.v2")

        assertThatThrownBy { service.generate(context) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.RAG_INVALID_REQUEST)
            }
        assertThat(vectorStore.searchCalls).isEqualTo(0)
    }

    @Test
    fun `generate returns insufficient evidence when official docs are not retrieved`() {
        val result = service(vectorStore = FakeVectorStore(emptyList()))
            .generate(readFixture("today-record-feedback-watering.json"))

        assertThat(result.audit.warnings).contains("no_retrieved_documents")
        assertThat(result.result.riskLevel).isEqualTo(CoachingRiskLevel.UNKNOWN)
        assertThat(result.result.limitations).contains("검색된 공식문서 근거가 없습니다.")
    }

    @Test
    fun `generate retrieves official documents with planned queries and audits structured response`() {
        val vectorStore = FakeVectorStore(listOf(officialDocument("doc-1")))
        val chatClient = FakeChatClient(structuredResult("doc-1"))
        val result = service(vectorStore = vectorStore, chatClient = chatClient)
            .generate(readFixture("today-record-feedback-watering.json"), topK = 2)

        assertThat(result.audit.citations).containsExactly("doc-1")
        assertThat(result.model.chat).isEqualTo("test-chat")
        assertThat(vectorStore.requests.map { it.query }).contains("참당귀 물주기 재배 관리 약용작물")
        assertThat(vectorStore.requests.map { it.filterExpression }).containsOnly("sourceType == 'TECH_DOCUMENT'")
        assertThat(chatClient.requestSpec.systemText).contains("약용작물 영농기록 피드백")
        assertThat(chatClient.requestSpec.userText).contains("오전 흙 표면이 말라 보여 점적 관수함.")
    }

    private fun service(
        vectorStore: FakeVectorStore,
        chatClient: ChatClient = FakeChatClient(structuredResult("doc-1"))
    ): TodayRecordFeedbackService {
        return TodayRecordFeedbackService(
            chatClient = chatClient,
            vectorStore = vectorStore,
            contextValidator = TodayRecordFeedbackContextValidator(),
            queryPlanner = RecordFeedbackRetrievalQueryPlanner(),
            promptBuilder = RecordFeedbackPromptBuilder(),
            outputValidator = CoachingStructuredOutputValidator(),
            ragProperties = RagProperties(
                chat = RagProperties.Chat(model = "test-chat"),
                embedding = RagProperties.Embedding(model = "test-embedding")
            )
        )
    }

    private fun readFixture(name: String): TodayRecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return objectMapper.readValue(resource)
    }

    private fun officialDocument(id: String): Document {
        return Document.builder()
            .id(id)
            .text("약용작물 관수 후 토양 수분과 배수 상태를 확인한다.")
            .metadata("sourceType", RagSourceType.TECH_DOCUMENT.name)
            .metadata("documentTitle", "농업기술길잡이 007 약용작물")
            .metadata("page", 123)
            .build()
    }

    private fun structuredResult(citationId: String): CoachingStructuredResult {
        return CoachingStructuredResult(
            summary = "관수 판단은 최근 건조 조건을 반영했습니다.",
            riskLevel = CoachingRiskLevel.LOW,
            confidence = 0.74,
            observations = listOf(CoachingObservation("건조 조건", "최근 7일 강수량이 적습니다.", listOf(citationId))),
            diagnosis = "현재 기록만으로 과습 위험은 높지 않습니다.",
            recommendations = listOf(
                CoachingRecommendation(CoachingPriority.MEDIUM, "다음 관수 전 토양 수분 확인", "건조 조건", null, listOf(citationId))
            ),
            nextActions = listOf(CoachingNextAction(CoachingActionDue.NEXT_CHECK, "잎 처짐과 토양 상태 기록", listOf(citationId))),
            followUpQuestions = listOf("배수가 잘 되지 않는 구역이 있나요?"),
            citations = listOf(CoachingCitationRef(citationId, "농업기술길잡이 007 약용작물", RagSourceType.TECH_DOCUMENT))
        )
    }

    private class FakeVectorStore(
        private val documents: List<Document>
    ) : VectorStore {
        val requests = mutableListOf<SearchRequest>()
        val searchCalls: Int
            get() = requests.size

        override fun add(documents: List<Document>) = Unit

        override fun delete(idList: List<String>) = Unit

        override fun delete(filterExpression: Filter.Expression) = Unit

        override fun similaritySearch(request: SearchRequest): List<Document> {
            requests += request
            return documents
        }
    }

    private class FakeChatClient(
        private val result: CoachingStructuredResult
    ) : ChatClient {
        val requestSpec = FakeRequestSpec(FakeCallResponseSpec(result))

        override fun prompt(): ChatClient.ChatClientRequestSpec = requestSpec

        override fun prompt(content: String): ChatClient.ChatClientRequestSpec = requestSpec

        override fun prompt(prompt: Prompt): ChatClient.ChatClientRequestSpec = requestSpec

        override fun mutate(): ChatClient.Builder = error("mutate is not used")
    }

    private class FakeRequestSpec(
        private val callResponseSpec: ChatClient.CallResponseSpec
    ) : ChatClient.ChatClientRequestSpec {
        var systemText: String = ""
        var userText: String = ""

        override fun mutate(): ChatClient.Builder = error("mutate is not used")
        override fun advisors(advisorSpecConsumer: Consumer<ChatClient.AdvisorSpec>): ChatClient.ChatClientRequestSpec = this
        override fun advisors(vararg advisors: Advisor): ChatClient.ChatClientRequestSpec = this
        override fun advisors(advisors: List<Advisor>): ChatClient.ChatClientRequestSpec = this
        override fun messages(vararg messages: Message): ChatClient.ChatClientRequestSpec = this
        override fun messages(messages: List<Message>): ChatClient.ChatClientRequestSpec = this
        override fun <T : ChatOptions> options(chatOptions: T): ChatClient.ChatClientRequestSpec = this
        override fun toolNames(vararg toolNames: String): ChatClient.ChatClientRequestSpec = this
        override fun tools(vararg tools: Any): ChatClient.ChatClientRequestSpec = this
        override fun toolCallbacks(vararg toolCallbacks: ToolCallback): ChatClient.ChatClientRequestSpec = this
        override fun toolCallbacks(toolCallbacks: List<ToolCallback>): ChatClient.ChatClientRequestSpec = this
        override fun toolCallbacks(vararg toolCallbackProviders: ToolCallbackProvider): ChatClient.ChatClientRequestSpec = this
        override fun toolContext(toolContext: Map<String, Any>): ChatClient.ChatClientRequestSpec = this
        override fun system(text: String): ChatClient.ChatClientRequestSpec {
            systemText = text
            return this
        }
        override fun system(resource: Resource, charset: Charset): ChatClient.ChatClientRequestSpec = this
        override fun system(resource: Resource): ChatClient.ChatClientRequestSpec = this
        override fun system(systemSpecConsumer: Consumer<ChatClient.PromptSystemSpec>): ChatClient.ChatClientRequestSpec = this
        override fun user(text: String): ChatClient.ChatClientRequestSpec {
            userText = text
            return this
        }
        override fun user(resource: Resource, charset: Charset): ChatClient.ChatClientRequestSpec = this
        override fun user(resource: Resource): ChatClient.ChatClientRequestSpec = this
        override fun user(userSpecConsumer: Consumer<ChatClient.PromptUserSpec>): ChatClient.ChatClientRequestSpec = this
        override fun templateRenderer(templateRenderer: TemplateRenderer): ChatClient.ChatClientRequestSpec = this
        override fun call(): ChatClient.CallResponseSpec = callResponseSpec
        override fun stream(): ChatClient.StreamResponseSpec = error("stream is not used")
    }

    private class FakeCallResponseSpec(
        private val result: CoachingStructuredResult
    ) : ChatClient.CallResponseSpec {
        override fun <T : Any> entity(type: Class<T>): T = type.cast(result)
        override fun <T : Any> entity(type: ParameterizedTypeReference<T>): T = error("entity type reference is not used")
        override fun <T : Any> entity(structuredOutputConverter: StructuredOutputConverter<T>): T = error("entity converter is not used")
        override fun chatClientResponse(): ChatClientResponse = error("chatClientResponse is not used")
        override fun chatResponse(): ChatResponse = error("chatResponse is not used")
        override fun content(): String = error("content is not used")
        override fun <T : Any> responseEntity(type: Class<T>): ResponseEntity<ChatResponse, T> = error("responseEntity class is not used")
        override fun <T : Any> responseEntity(type: ParameterizedTypeReference<T>): ResponseEntity<ChatResponse, T> = error("responseEntity type reference is not used")
        override fun <T : Any> responseEntity(structuredOutputConverter: StructuredOutputConverter<T>): ResponseEntity<ChatResponse, T> = error("responseEntity converter is not used")
    }
}
```

- [ ] **Step 2: Run service tests and verify they fail**

Run:

```bash
./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackServiceTest"
```

Expected: compilation fails because `TodayRecordFeedbackService` and `TodayRecordFeedbackResult` do not exist.

- [ ] **Step 3: Add service implementation**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackService.kt`:

```kotlin
package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.application.coaching.rag.CoachingRiskLevel
import com.chamchamcham.application.coaching.rag.CoachingStructuredOutputValidator
import com.chamchamcham.application.coaching.rag.CoachingStructuredResult
import com.chamchamcham.application.coaching.rag.RagAuditResult
import com.chamchamcham.application.coaching.rag.RagAuditStatus
import com.chamchamcham.application.coaching.rag.RagModelInfo
import com.chamchamcham.application.coaching.rag.RagProperties
import com.chamchamcham.application.coaching.rag.RagSourceType
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.Document
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor
import org.springframework.ai.rag.retrieval.search.DocumentRetriever
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

data class TodayRecordFeedbackResult(
    val result: CoachingStructuredResult,
    val audit: RagAuditResult,
    val model: RagModelInfo,
    val contextWarnings: List<String>
)

@Service
class TodayRecordFeedbackService(
    private val chatClient: ChatClient,
    private val vectorStore: VectorStore,
    private val contextValidator: TodayRecordFeedbackContextValidator,
    private val queryPlanner: RecordFeedbackRetrievalQueryPlanner,
    private val promptBuilder: RecordFeedbackPromptBuilder,
    private val outputValidator: CoachingStructuredOutputValidator,
    private val ragProperties: RagProperties
) {
    fun generate(context: TodayRecordFeedbackContext, topK: Int? = null): TodayRecordFeedbackResult {
        val validation = contextValidator.requireValid(context)
        val perQueryTopK = normalizeTopK(topK)
        val queries = queryPlanner.plan(context)
        val documents = retrieveDocuments(queries, perQueryTopK)

        if (documents.isEmpty()) {
            val result = CoachingStructuredResult.insufficientEvidence(
                "현재 공식문서 근거만으로는 기록을 판단할 수 없습니다."
            ).copy(
                riskLevel = CoachingRiskLevel.UNKNOWN,
                limitations = listOf("검색된 공식문서 근거가 없습니다.")
            )
            return TodayRecordFeedbackResult(
                result = result,
                audit = RagAuditResult(RagAuditStatus.WARN, listOf("no_retrieved_documents"), emptyList()),
                model = modelInfo(),
                contextWarnings = validation.warnings
            )
        }

        val evidence = documents.map { it.toRecordFeedbackEvidence() }
        val prompt = promptBuilder.build(context, queries, evidence)
        val advisor = RetrievalAugmentationAdvisor.builder()
            .documentRetriever(DocumentRetriever { documents })
            .build()

        val result = try {
            chatClient.prompt()
                .system(prompt.system)
                .user(prompt.user)
                .advisors(advisor)
                .call()
                .entity(CoachingStructuredResult::class.java)
        } catch (exception: BusinessException) {
            throw exception
        } catch (_: RuntimeException) {
            throw BusinessException(ErrorCode.RAG_STRUCTURED_OUTPUT_INVALID)
        }

        val allowedCitationIds = documents.map { it.id }.toSet()
        val audit = outputValidator.validate(result, allowedCitationIds)

        return TodayRecordFeedbackResult(
            result = result,
            audit = audit,
            model = modelInfo(),
            contextWarnings = validation.warnings
        )
    }

    private fun normalizeTopK(topK: Int?): Int {
        val value = topK ?: ragProperties.retrieval.topKDefault
        if (value !in 1..ragProperties.retrieval.topKMax) {
            throw BusinessException(ErrorCode.RAG_INVALID_REQUEST)
        }
        return value
    }

    private fun retrieveDocuments(
        queries: List<RecordFeedbackRetrievalQuery>,
        perQueryTopK: Int
    ): List<Document> {
        return queries
            .flatMap { query ->
                vectorStore.similaritySearch(
                    SearchRequest.builder()
                        .query(query.query)
                        .topK(perQueryTopK)
                        .filterExpression("sourceType == '${RagSourceType.TECH_DOCUMENT.name}'")
                        .build()
                )
            }
            .distinctBy { it.id }
    }

    private fun Document.toRecordFeedbackEvidence(): RecordFeedbackEvidence {
        return RecordFeedbackEvidence(
            id = id,
            title = metadata["documentTitle"]?.toString()
                ?: metadata["label"]?.toString()
                ?: id,
            page = metadata["page"]?.toString()?.toIntOrNull(),
            content = text ?: ""
        )
    }

    private fun modelInfo(): RagModelInfo {
        return RagModelInfo(
            embedding = ragProperties.embedding.model,
            chat = ragProperties.chat.model
        )
    }
}
```

- [ ] **Step 4: Run service tests and verify they pass**

Run:

```bash
./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackServiceTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit service**

Run from repository root:

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackService.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackServiceTest.kt
git commit -m "feat(rag): 기록 피드백 RAG 엔진 추가" -m "TodayRecordFeedbackContext를 입력으로 받아 공식문서 검색, 프롬프트 생성, 구조화 응답 검증까지 수행하는 RAG 엔진을 추가한다." -m "Constraint: farming-record 저장 모델과 assembler는 이 변경에서 제외" -m "Rejected: CoachingRagService에 record payload 경로를 직접 섞기 | 기존 interactive query 경로와 입력 계약이 달라 책임이 흐려짐" -m "Confidence: medium" -m "Scope-risk: moderate" -m "Tested: ./gradlew :application:test --tests \"com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackServiceTest\""
```

## Task 7: Run Focused Regression Suite

**Files:**

- Existing tests under `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/**`

- [ ] **Step 1: Run record-feedback tests**

Run from `/Users/wingwogus/Projects/ChamChamCham/backend`:

```bash
./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.*"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run existing RAG application tests**

Run:

```bash
./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.*"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run application module tests**

Run:

```bash
./gradlew :application:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit any test-only fixes from regression**

If the regression run required small fixes, stage only the touched RAG files and commit:

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag backend/application/src/test/resources/coaching/rag
git commit -m "test(rag): 기록 피드백 회귀 테스트 정리" -m "새 기록 피드백 RAG 엔진과 기존 RAG 테스트가 함께 통과하도록 테스트 기대값과 기본 응답 필드를 정리한다." -m "Confidence: high" -m "Scope-risk: narrow" -m "Tested: ./gradlew :application:test"
```

If no files changed after the regression run, do not create an empty commit.

## Task 8: Final Verification

**Files:**

- No planned file edits.

- [ ] **Step 1: Verify no farming-record write model files changed**

Run from repository root:

```bash
git diff --name-only HEAD~6..HEAD
```

Expected: the output does not include files under:

```text
backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/
backend/api/src/main/kotlin/com/chamchamcham/api/farming/
```

- [ ] **Step 2: Verify context naming**

Run:

```bash
rg -n "TodayDiary|today-diary|diary-feedback" backend/application/src/main/kotlin backend/application/src/test/kotlin backend/application/src/test/resources docs/superpowers
```

Expected: no matches.

- [ ] **Step 3: Verify new context naming exists**

Run:

```bash
rg -n "TodayRecordFeedbackContext|record-feedback-context.v1|today-record-feedback" backend/application/src/main/kotlin backend/application/src/test/kotlin backend/application/src/test/resources docs/superpowers
```

Expected: matches in the new record-feedback package, fixtures, and design/plan docs.

- [ ] **Step 4: Run backend application tests one final time**

Run from `/Users/wingwogus/Projects/ChamChamCham/backend`:

```bash
./gradlew :application:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Check working tree**

Run from repository root:

```bash
git status --short
```

Expected: only pre-existing unrelated untracked paths may remain:

```text
?? backend/docs/db/crop-seed.sql
?? data/
?? outputs/
```

## Self-Review

Spec coverage:

- `TodayRecordFeedbackContext` payload contract: Task 1.
- `schemaVersion`: Tasks 1 and 2.
- fixture-only RAG tests without record DB setup: Tasks 1, 3, 4, 6.
- no farming-record write model changes: Scope Check and Task 8.
- work-type statistics in context: Task 1 fixtures and Task 4 prompt.
- weather context in prompt and retrieval planning: Tasks 3 and 4.
- official-document retrieval with citations: Task 6.
- structured JSON output with risk, confidence, record quality, limitations, and citations: Tasks 5 and 6.
- later `record -> payload assembler` split: Scope Check and Task 8.

Placeholder scan:

- No undecided placeholder markers remain.
- No unspecified "add appropriate validation" steps.
- Every new class/test file has concrete code.
- The only conditional step is Task 7 Step 4, and it explicitly says not to create an empty commit when no files changed.

Type consistency:

- The context class is consistently `TodayRecordFeedbackContext`.
- The schema string is consistently `record-feedback-context.v1`.
- Fixtures are consistently `today-record-feedback-*.json`.
- Work type enum is consistently `TodayRecordWorkType`.
- The new service is isolated as `TodayRecordFeedbackService` and does not overload the existing interactive `CoachingRagService`.
