//
//  Crop.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

struct CropCategory: Identifiable, Hashable, Sendable {
    let code: String
    let label: String

    var id: String { code }
}

struct Crop: Identifiable, Hashable, Sendable {
    /// Crop selection is capped at 5 everywhere a picker exists (onboarding, farm add/edit,
    /// community board add/compose) — a single shared limit, not a per-screen choice.
    static let maxSelectionCount = 5

    let id: UUID
    let name: String
    let categoryCode: String
    let categoryLabel: String

    var category: String { categoryLabel }

    init(id: UUID, name: String, categoryCode: String, categoryLabel: String) {
        self.id = id
        self.name = name
        self.categoryCode = categoryCode
        self.categoryLabel = categoryLabel
    }

    init(id: UUID, name: String, category: String) {
        self.init(id: id, name: name, categoryCode: category, categoryLabel: category)
    }
}
