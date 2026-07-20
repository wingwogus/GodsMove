<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# FarmingReportResponse

> ⬆ 상위: [API 명세서](README.md)

### Fields
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
### Rule
리포트는 조회 시 실시간 생성하며 DB에 저장하지 않는다. AI 요약 실패 시 통계 정보만 표시한다.
