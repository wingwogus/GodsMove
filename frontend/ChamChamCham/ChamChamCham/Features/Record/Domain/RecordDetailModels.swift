//
//  RecordDetailModels.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import Foundation

/// One farming record's full detail (Figma `심기 작업 결과 - 씨앗`, node `1498:21864`). Projection of the
/// backend `RecordDetailResponse` (`GET /farming-records/{id}`). The only part that varies by `workType` is the
/// `infoRows` list — the header (제목/메모/사진) and AI 코칭 slot are common across all eight types.
///
/// The AI 코칭 ("참참참의 코칭") section is a separate resource (`GET /farming-records/{id}/feedback`), modeled by
/// `RecordCoaching` and loaded independently by the view model — it is intentionally not part of this type.
struct RecordDetail: Identifiable, Sendable, Hashable {
    let id: UUID
    let workType: WorkType
    let workedAt: Date
    let weatherCondition: String
    let weatherTemperature: Int
    let farmName: String
    let cropName: String
    let memo: String
    let imageUrls: [String]
    /// workType별 '작업 정보' 항목(라벨-값). 미입력 항목은 매핑 단계에서 이미 제외돼 있음.
    let infoRows: [RecordInfoRow]
}

/// One 라벨-값 row inside the 작업 정보 카드. `id` is the label (unique within a card).
struct RecordInfoRow: Identifiable, Hashable, Sendable {
    var id: String { label }
    let label: String
    let value: String
}
