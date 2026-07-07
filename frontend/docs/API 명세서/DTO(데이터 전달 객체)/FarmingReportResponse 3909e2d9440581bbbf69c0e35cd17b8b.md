# FarmingReportResponse

API 분류: API Response
태그: [리포트] 리포트

## Fields

- periodType: enum, required. LAST_7_DAYS, LAST_30_DAYS, ALL, CUSTOM.
- periodStartsOn: date, optional.
- periodEndsOn: date, optional.
- farmId: uuid, optional.
- cropId: uuid, optional.
- totalRecordCount: number, required.
- workTypeDistribution: object[], required.
- cropStatistics: object[], required.
- riskSignals: string[], optional.
- aiSummary: string | null, optional.
- generatedAt: datetime, required.

## Rule

리포트는 조회 시 실시간 생성하며 DB에 저장하지 않는다. AI 요약 실패 시 통계 정보만 표시한다.