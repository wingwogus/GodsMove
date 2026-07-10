# FarmingReportResponse

API 분류: API Response
태그: [리포트] 리포트

## Response Shapes

- Current: `{ "current": FarmingReportDetail | null, "previous": FarmingReportDetail | null }`
- List: `{ "items": FarmingReportMetadata[], "nextCursor": string | null }`
- Detail: `{ "selected": FarmingReportDetail, "previous": FarmingReportDetail | null }`

`current` 응답에서 ACTIVE 리포트가 없으면 404가 아니라 `current: null`이다. 목록은 `COMPLETED` metadata만 반환하며 `statistics`를 포함하지 않는다. 상세는 선택한 주기와 직전 완료 주기의 전체 통계를 함께 반환한다. 직전 주기는 지난해가 아니라 바로 이전 COMPLETED 주기다.

## FarmingReportMetadata Fields

- id: uuid, required.
- farmId: uuid, required.
- farmName: string, required.
- cropId: uuid, required.
- cropName: string, required.
- status: enum, required. `COMPLETED`.
- startsAt: datetime, required.
- endsAt: datetime, required.
- startBasis: enum, required. `FIRST_RECORD`, `AFTER_PREVIOUS_FINAL_HARVEST`.
- sourceRevision: number, required.

## FarmingReportDetail Fields

- id: uuid, required.
- farmId: uuid, required.
- farmName: string, required.
- cropId: uuid, required.
- cropName: string, required.
- status: enum, required. `ACTIVE`, `COMPLETED`.
- startsAt: datetime, required.
- endsAt: datetime | null, required.
- startBasis: enum, required. `FIRST_RECORD`, `AFTER_PREVIOUS_FINAL_HARVEST`.
- finalHarvestRecordId: uuid | null, required.
- statisticsSchemaVersion: number, required.
- sourceRevision: number, required.
- statistics: CycleReportStatistics, required.

## CycleReportStatistics

8개 작업 블록을 항상 포함한다.

- planting
- watering
- fertilizing
- pestControl
- weeding
- pruning
- harvest
- etc

작업 기록이 없는 블록도 `0`, `null`, `[]` 기본값으로 존재한다. 최상위 `common` 객체는 없으며, 각 작업 객체가 공통 필드를 직접 가진다.

## 작업 객체 공통 필드

| Field | Type | Empty value |
| --- | --- | --- |
| recordCount | number | 0 |
| firstWorkedOn | date \| null | null |
| lastWorkedOn | date \| null | null |
| workedDayCount | number | 0 |
| averageIntervalDays | number \| null | null |
| photoAttachedRecordCount | number | 0 |
| photoAttachmentRatePct | number \| null | null |
| weatherDistribution | object[] | [] |
| averageTemperatureC | number \| null | null |

## 작업별 추가 필드

- planting: propagationMethods.
- watering: amountDistribution, methodDistribution.
- fertilizing: totalAmountKg, averageAmountKg, amountCoverage, materialCategories, methodDistribution, categoryMethods.
- pestControl: categoryDistribution, pesticideAmounts, categoryAmounts, totalSprayAmountLiters, sprayAmountCoverage, targets.
- weeding: methodDistribution.
- pruning: 추가 필드 없음.
- harvest: totalAmountKg, averageAmountKg, amountCoverage, firstHarvestedOn, lastHarvestedOn, medicinalParts, finalGrowthPeriodMonths, growthPeriodRangeMonths.
- etc: 추가 필드 없음.

관수 통계는 횟수와 간격을 제공하지만 수분 부족·과다·적정 판단을 추론하지 않는다. 주기 경계는 마지막 수확 기록을 기준으로 하며 일부 수확은 주기를 닫지 않는다.

## Example

아래 JSON은 핵심 shape를 보여주기 위한 축약 예시다. 실제 응답의 각 작업 객체는 위 표의 9개 공통 필드를 모두 가진다.

```json
{
  "selected": {
    "id": "uuid",
    "status": "COMPLETED",
    "startsAt": "2026-03-01T09:00:00",
    "endsAt": "2026-06-30T09:00:00",
    "statisticsSchemaVersion": 1,
    "sourceRevision": 5,
    "statistics": {
      "planting": { "recordCount": 1, "propagationMethods": [] },
      "watering": {
        "recordCount": 8,
        "averageIntervalDays": 12.0,
        "amountDistribution": [],
        "methodDistribution": []
      },
      "fertilizing": { "recordCount": 4, "materialCategories": [] },
      "pestControl": { "recordCount": 2, "pesticideAmounts": [] },
      "weeding": { "recordCount": 1, "methodDistribution": [] },
      "pruning": { "recordCount": 0 },
      "harvest": {
        "recordCount": 3,
        "totalAmountKg": 30.0,
        "amountCoverage": { "recordedCount": 2, "targetCount": 3 }
      },
      "etc": { "recordCount": 0 }
    }
  },
  "previous": null
}
```

## Rule

리포트는 DB에 저장된 회원·밭·작물 주기 단위 projection을 조회한다. 완료 주기는 마지막 수확 기록을 포함해 닫히며 현재 리포트의 비교 대상은 지난해가 아니라 직전 완료 주기다.
